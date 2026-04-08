from __future__ import annotations

import dataclasses
import json
import pathlib
from typing import Optional

import requests
import yaml

from .analyze import ProjectAnalysis
from .generate import ComposeArtifacts


@dataclasses.dataclass(frozen=True)
class OllamaConfig:
    base_url: str = "http://localhost:11434"


def maybe_refine_with_ollama(
    *,
    analysis: ProjectAnalysis,
    draft: ComposeArtifacts,
    model: str,
    compose_yaml_preview: Optional[str] = None,
) -> Optional[ComposeArtifacts]:
    cfg = OllamaConfig()
    if not _ollama_is_available(cfg):
        return None

    prompt = _build_prompt(analysis, draft, compose_yaml_preview=compose_yaml_preview)

    try:
        resp = requests.post(
            f"{cfg.base_url}/api/chat",
            json={
                "model": model,
                "messages": [
                    {"role": "system", "content": _system_rules()},
                    {"role": "user", "content": prompt},
                ],
                "stream": False,
            },
            timeout=30,
        )
        resp.raise_for_status()
        data = resp.json()
        content = data.get("message", {}).get("content")
        if not content:
            return None

        refined = _parse_refinement_json(content)
        if refined is None:
            return None

        # Apply refinements service-by-service without inventing.
        updated_services = []
        by_name = {s["name"]: s for s in refined.get("services", []) if isinstance(s, dict)}

        for s in draft.services:
            upd = by_name.get(s.name)
            if not upd:
                updated_services.append(s)
                continue

            port = s.port
            if isinstance(upd.get("port"), int) and 1 <= upd["port"] <= 65535:
                port = int(upd["port"])

            db = s.db
            if upd.get("db") in {None, "postgres", "mysql", "mongo", "redis"}:
                db = upd.get("db")

            hp = s.health_path
            if isinstance(upd.get("health_path"), str) and upd["health_path"].startswith("/"):
                hp = upd["health_path"].strip()

            updated_services.append(
                type(s)(
                    name=s.name,
                    service_dir=s.service_dir,
                    project_kind=s.project_kind,
                    runtime=s.runtime,
                    port=port,
                    db=db,
                    health_path=hp,
                    dockerfile=s.dockerfile,
                )
            )

        return ComposeArtifacts(project_dir=draft.project_dir, services=updated_services)

    except Exception:
        return None


def _ollama_is_available(cfg: OllamaConfig) -> bool:
    try:
        r = requests.get(f"{cfg.base_url}/api/tags", timeout=2)
        return r.status_code == 200
    except Exception:
        return False


def _system_rules() -> str:
    return (
        "You are helping refine docker-compose generation decisions for a repository. "
        "Hard rules: DO NOT invent unknown configuration values. "
        "Only output values that are strongly supported by the provided evidence. "
        "If unknown, return null. "
        "Before responding, self-check your output against the evidence and the compose preview (if provided). "
        "Return ONLY valid JSON matching the requested schema."
    )


def _build_prompt(
    analysis: ProjectAnalysis,
    draft: ComposeArtifacts,
    *,
    compose_yaml_preview: Optional[str],
) -> str:
    summary = {
        "services": [
            {
                "name": s.name,
                "dir": str(s.service_dir),
                "project_kind": s.project_kind,
                "runtime": s.runtime,
                "detected_port": s.port,
                "detected_db": s.db,
                "detected_health_path": s.health_path,
                "has_dockerfile": s.has_dockerfile,
            }
            for s in analysis.services
        ]
    }

    draft_summary = {
        "services": [
            {"name": s.name, "port": s.port, "db": s.db, "health_path": s.health_path}
            for s in draft.services
        ]
    }

    evidence = _collect_evidence_snippets(analysis)

    compose_preview = None
    if compose_yaml_preview:
        # Keep the prompt bounded to avoid blowing context on large monorepos.
        compose_preview = compose_yaml_preview.strip()
        if len(compose_preview) > 12_000:
            compose_preview = compose_preview[:12_000] + "\n# ...(truncated)"

    schema = {
        "services": [
            {
                "name": "string (must match an existing service name)",
                "port": "integer or null",
                "db": "postgres|mysql|mongo|redis|null",
                "health_path": "string starting with / or null",
            }
        ]
    }

    task = (
        "Task: Analyze the repository evidence and refine ONLY the values you are confident about. "
        "If you are not sure about a value, return null for it. "
        "\n\nSelf-check requirements (must do before final JSON):\n"
        "1) Verify any proposed port/db/health_path is supported by the provided snippets.\n"
        "2) If the evidence is ambiguous or conflicting, return null (do not guess defaults).\n"
        "3) Cross-check against the current draft and the compose preview: if the draft appears inconsistent with evidence, propose a correction; otherwise keep null.\n"
        "4) Never invent credentials, URLs, host ports, build contexts, image tags, or additional services.\n"
        "\nOutput must be JSON matching this schema:\n"
    )

    return (
        "Evidence summary (json):\n"
        + json.dumps(summary, indent=2)
        + "\n\n"
        + "Evidence snippets (json, truncated):\n"
        + json.dumps(evidence, indent=2)
        + ("\n\nCompose preview (docker-compose.yml, truncated):\n" + compose_preview if compose_preview else "")
        + "\n\n"
        + "Current draft decisions (json):\n"
        + json.dumps(draft_summary, indent=2)
        + "\n\n"
        + task
        + json.dumps(schema, indent=2)
    )


def _collect_evidence_snippets(analysis: ProjectAnalysis) -> dict:
    """Collect small, relevant file snippets per service to ground LLM refinements."""

    max_total_chars = 25_000
    max_file_chars = 3_000

    def read_snip(path: pathlib.Path) -> Optional[str]:
        nonlocal max_total_chars
        if max_total_chars <= 0:
            return None
        if not path.exists() or not path.is_file():
            return None
        try:
            if path.stat().st_size > 2_000_000:
                return None
            text = path.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            return None
        text = text.strip()
        if not text:
            return None
        snip = text[: min(len(text), max_file_chars)]
        max_total_chars -= len(snip)
        return snip

    out: dict[str, dict[str, str]] = {}

    for s in analysis.services:
        files: list[pathlib.Path] = [
            s.service_dir / "README.md",
            s.service_dir / "readme.md",
            s.service_dir / ".env.example",
            s.service_dir / ".env.sample",
            s.service_dir / ".env",
            s.service_dir / "package.json",
            s.service_dir / "pyproject.toml",
            s.service_dir / "requirements.txt",
            s.service_dir / "Dockerfile",
            s.service_dir / "docker-compose.yml",
        ]

        # Java ports
        files.extend(
            [
                s.service_dir / "src" / "main" / "resources" / "application.properties",
                s.service_dir / "src" / "main" / "resources" / "application.yml",
                s.service_dir / "src" / "main" / "resources" / "application.yaml",
            ]
        )

        # .NET ports
        files.extend(list(s.service_dir.glob("**/Properties/launchSettings.json"))[:2])

        snippets: dict[str, str] = {}
        for f in files:
            sn = read_snip(f)
            if sn is None:
                continue
            # Use repo-relative-ish path for readability
            try:
                key = str(f.relative_to(analysis.project_dir)).replace("\\", "/")
            except Exception:
                key = str(f)
            snippets[key] = sn

            if max_total_chars <= 0:
                break

        out[s.name] = snippets

    return out


def _parse_refinement_json(content: str) -> Optional[dict]:
    # Prefer fenced json, else try raw.
    text = _extract_fenced_json(content) or content
    text = text.strip()
    try:
        data = json.loads(text)
        if isinstance(data, dict) and "services" in data:
            return data
    except Exception:
        return None
    return None


def _extract_fenced_json(content: str) -> Optional[str]:
    import re

    m = re.search(r"```(?:json)\s*(.*?)```", content, flags=re.DOTALL | re.IGNORECASE)
    if not m:
        return None
    return m.group(1).strip()
