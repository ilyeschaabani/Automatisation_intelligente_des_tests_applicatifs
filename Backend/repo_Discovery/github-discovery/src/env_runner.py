"""Docker Compose environment runner.

This module is intentionally conservative:
- It never runs Docker unless Config.allow_docker is enabled.
- It prefers an existing docker-compose file in the repo.
- If none exists, it can generate a draft compose in a temp folder, but only when it
  can infer essential facts (like an exposed HTTP port) from the repo without guessing.

The goal is to bring up an API environment so downstream test execution can run
against a known base URL.
"""

from __future__ import annotations

import re
import subprocess
import tempfile
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import yaml

from .config import Config


@dataclass
class EnvActionResult:
    status: str  # running | stopped | needs_user_input | blocked | error
    message: str
    compose_file: Optional[str] = None
    project_name: Optional[str] = None
    detected_base_url: Optional[str] = None
    questions: Optional[List[Dict[str, Any]]] = None
    details: Optional[Dict[str, Any]] = None


def _compose_cmd(cfg: Config) -> List[str]:
    # Default: ["docker", "compose", ...]
    cmd = [cfg.docker_compose_command]
    sub = (cfg.docker_compose_subcommand or "").strip()
    if sub:
        cmd.append(sub)
    return cmd


def _run(cmd: List[str], cwd: Optional[Path] = None, timeout: int = 900) -> Tuple[int, str, str]:
    p = subprocess.run(
        cmd,
        cwd=str(cwd) if cwd else None,
        capture_output=True,
        text=True,
        timeout=timeout,
        check=False,
    )
    return p.returncode, p.stdout or "", p.stderr or ""


def _safe_rel_path(repo_root: Path, p: Path) -> Optional[str]:
    try:
        return p.relative_to(repo_root).as_posix()
    except Exception:
        return None


def _find_compose_candidates(repo_root: Path) -> List[Path]:
    preferred = [
        repo_root / "docker-compose.yml",
        repo_root / "docker-compose.yaml",
        repo_root / "compose.yml",
        repo_root / "compose.yaml",
    ]
    out: List[Path] = [p for p in preferred if p.exists() and p.is_file()]

    for p in list(repo_root.glob("docker-compose.*.yml")) + list(repo_root.glob("docker-compose.*.yaml")):
        if p.exists() and p.is_file() and p not in out:
            out.append(p)

    if not out:
        # bounded depth search (<= 3 directories deep)
        try:
            for p in repo_root.rglob("docker-compose.y*ml"):
                rel = _safe_rel_path(repo_root, p)
                if rel is None:
                    continue
                if len(Path(rel).parts) <= 4 and p.is_file():
                    out.append(p)
                    if len(out) >= 10:
                        break
        except Exception:
            pass

    return out


def _compose_has_http_candidate(compose_path: Path) -> bool:
    try:
        text = compose_path.read_text(encoding="utf-8", errors="replace")
        data = yaml.safe_load(text) if text else None
    except Exception:
        return False

    if not isinstance(data, dict):
        return False

    services = data.get("services")
    if not isinstance(services, dict):
        return False

    def _is_infra_service(name: str, svc: Any) -> bool:
        nl = (name or "").lower()
        if any(k in nl for k in [
            "postgres",
            "mysql",
            "mariadb",
            "mongo",
            "mongodb",
            "redis",
            "rabbitmq",
            "kafka",
            "zookeeper",
            "elasticsearch",
            "opensearch",
            "pgadmin",
            "adminer",
        ]):
            return True
        if isinstance(svc, dict):
            img = (svc.get("image") or "").lower() if svc.get("image") else ""
            if img.startswith((
                "postgres",
                "postgis",
                "mysql",
                "mariadb",
                "mongo",
                "mongodb",
                "redis",
                "rabbitmq",
                "confluentinc/cp-kafka",
                "bitnami/kafka",
                "bitnami/zookeeper",
                "elasticsearch",
                "opensearchproject/opensearch",
                "dpage/pgadmin",
                "dpage/pgadmin4",
                "adminer",
            )):
                return True
        return False

    for name, svc in services.items():
        if _is_infra_service(str(name), svc):
            continue
        if not isinstance(svc, dict):
            continue
        ports = svc.get("ports")
        if isinstance(ports, list) and ports:
            return True
    return False


def _find_dockerfiles(repo_root: Path) -> List[Path]:
    out: List[Path] = []
    for p in repo_root.rglob("Dockerfile"):
        rel = _safe_rel_path(repo_root, p)
        if rel is None:
            continue
        if len(Path(rel).parts) <= 4 and p.is_file():
            out.append(p)
            if len(out) >= 10:
                break
    return out


def _infer_port_from_dockerfile(dockerfile: Path) -> Optional[int]:
    try:
        text = dockerfile.read_text(encoding="utf-8", errors="replace")
    except Exception:
        return None

    # EXPOSE 8080
    expose_re = re.compile(r"^\s*EXPOSE\s+(\d+)", re.IGNORECASE | re.MULTILINE)
    m = expose_re.search(text)
    if m:
        try:
            return int(m.group(1))
        except Exception:
            return None

    return None


def _infer_port_from_spring(repo_root: Path) -> Optional[int]:
    # Look for explicit server.port in common config files.
    candidates: List[Path] = []
    for rel in [
        "src/main/resources/application.properties",
        "src/main/resources/application.yml",
        "src/main/resources/application.yaml",
        "application.properties",
        "application.yml",
        "application.yaml",
    ]:
        p = repo_root / rel
        if p.exists() and p.is_file():
            candidates.append(p)

    # bounded search for application.* at depth <= 4
    if not candidates:
        try:
            for p in repo_root.rglob("application.properties"):
                rel = _safe_rel_path(repo_root, p)
                if rel and len(Path(rel).parts) <= 5:
                    candidates.append(p)
                    break
        except Exception:
            pass

    prop_re = re.compile(r"^\s*server\.port\s*=\s*(\d+)\s*$", re.MULTILINE)
    yml_re = re.compile(r"^\s*port\s*:\s*(\d+)\s*$", re.MULTILINE)

    for p in candidates:
        try:
            text = p.read_text(encoding="utf-8", errors="replace")
        except Exception:
            continue

        m = prop_re.search(text)
        if m:
            try:
                return int(m.group(1))
            except Exception:
                return None

        # YAML: only accept if it's under a server: block in a nearby context (very light heuristic)
        if "server:" in text:
            # find the first server: block and search for port inside it
            # (we keep it simple; this is best-effort)
            server_idx = text.find("server:")
            snippet = text[server_idx : server_idx + 2000]
            m2 = yml_re.search(snippet)
            if m2:
                try:
                    return int(m2.group(1))
                except Exception:
                    return None

    return None


def generate_compose_draft(
    *,
    repo_root: Path,
    output_dir: Path,
    dockerfile_path: Optional[str] = None,
    app_port: Optional[int] = None,
) -> EnvActionResult:
    """Generate a docker-compose file draft in output_dir.

    Only generates when:
    - Exactly one Dockerfile is found (depth <= 4)
    - A port can be inferred deterministically (Dockerfile EXPOSE or explicit server.port)

    Otherwise returns needs_user_input with questions.
    """

    chosen_dockerfile: Optional[Path] = None

    if dockerfile_path:
        candidate = repo_root / dockerfile_path
        if candidate.exists() and candidate.is_file() and candidate.name.lower() == "dockerfile":
            chosen_dockerfile = candidate
        else:
            return EnvActionResult(
                status="needs_user_input",
                message="Provided dockerfile_path is invalid or not found.",
                questions=[
                    {
                        "id": "dockerfile_path",
                        "question": "Provide a valid path (relative to repo root) to the Dockerfile.",
                    }
                ],
                details={"dockerfile_path": dockerfile_path},
            )

    dockerfiles = [chosen_dockerfile] if chosen_dockerfile else _find_dockerfiles(repo_root)
    if not dockerfiles:
        return EnvActionResult(
            status="needs_user_input",
            message="No docker-compose and no Dockerfile found; cannot generate a runnable compose automatically.",
            questions=[
                {
                    "id": "provide_compose_or_dockerfile",
                    "question": "Provide a docker-compose.yml or a Dockerfile (or tell me how to run the API) so I can generate one.",
                    "options": ["Add docker-compose.yml", "Add Dockerfile", "Provide run instructions"],
                }
            ],
        )

    if len(dockerfiles) > 1:
        opts = [(_safe_rel_path(repo_root, p) or str(p)) for p in dockerfiles]
        return EnvActionResult(
            status="needs_user_input",
            message="Multiple Dockerfiles found; select which one builds the API service.",
            questions=[
                {
                    "id": "dockerfile_path",
                    "question": "Which Dockerfile should be used for the API container?",
                    "options": opts,
                }
            ],
            details={"dockerfiles": opts},
        )

    dockerfile = dockerfiles[0]

    port = None
    if app_port is not None:
        try:
            port = int(app_port)
        except Exception:
            port = None

    port = port or _infer_port_from_dockerfile(dockerfile) or _infer_port_from_spring(repo_root)
    if not port:
        return EnvActionResult(
            status="needs_user_input",
            message="Dockerfile found but API port could not be inferred (no EXPOSE and no explicit server.port).",
            questions=[
                {
                    "id": "app_port",
                    "question": "What port does the API listen on inside the container (e.g. 8080)?",
                    "expected_format": "integer",
                }
            ],
            details={"dockerfile": _safe_rel_path(repo_root, dockerfile)},
        )

    output_dir.mkdir(parents=True, exist_ok=True)
    compose_path = output_dir / "docker-compose.generated.yml"

    # Use absolute context so the compose file can live in a temp directory.
    # (This keeps the user request: generate in tmp folder.)
    context_dir = dockerfile.parent
    dockerfile_rel = dockerfile.relative_to(context_dir).as_posix()

    compose_obj: Dict[str, Any] = {
        "version": "3.8",
        "services": {
            "app": {
                "build": {
                    "context": str(context_dir),
                    "dockerfile": dockerfile_rel,
                },
                "ports": [f"{port}:{port}"],
            }
        },
    }

    compose_path.write_text(yaml.safe_dump(compose_obj, sort_keys=False), encoding="utf-8")

    return EnvActionResult(
        status="stopped",
        message="Generated a draft docker-compose file.",
        compose_file=str(compose_path),
        detected_base_url=f"http://localhost:{port}",
        details={
            "dockerfile": str(dockerfile),
            "port": port,
        },
    )


def start_environment(
    *,
    cfg: Config,
    repo_root: Path,
    openapi_obj: Optional[Dict[str, Any]],
    job_id: str,
    overrides: Optional[Dict[str, Any]] = None,
) -> EnvActionResult:
    if not cfg.allow_docker:
        return EnvActionResult(
            status="blocked",
            message="Docker execution is disabled. Set DISCOVERY_ALLOW_DOCKER=true to allow docker compose up.",
        )

    # Basic docker availability check
    rc, _, err = _run([cfg.docker_compose_command, "--version"], timeout=30)
    if rc != 0:
        return EnvActionResult(status="error", message="Docker CLI not available.", details={"stderr": err})

    # Determine compose file
    xdisc = (openapi_obj or {}).get("x-discovery") if isinstance(openapi_obj, dict) else None
    run = xdisc.get("run") if isinstance(xdisc, dict) else None
    compose_path: Optional[Path] = None

    if isinstance(run, dict) and isinstance(run.get("compose_path"), str) and run.get("compose_path"):
        candidate = repo_root / str(run.get("compose_path"))
        if candidate.exists() and candidate.is_file():
            compose_path = candidate

    if compose_path is None and isinstance(run, dict):
        cands = run.get("compose_candidates")
        if isinstance(cands, list):
            for rel in cands:
                if not isinstance(rel, str) or not rel.strip():
                    continue
                p = repo_root / rel
                if p.exists() and p.is_file() and _compose_has_http_candidate(p):
                    compose_path = p
                    break

    # Fallback: any compose (even if infra-only) — but only if it exists.
    if compose_path is None:
        for p in _find_compose_candidates(repo_root):
            if p.exists() and p.is_file():
                compose_path = p
                break

    # Generate draft in temp if none
    generated_dir: Optional[Path] = None
    if compose_path is None:
        generated_dir = Path(tempfile.gettempdir()) / f"gd_env_{job_id}"
        ov = overrides or {}
        draft = generate_compose_draft(
            repo_root=repo_root,
            output_dir=generated_dir,
            dockerfile_path=ov.get("dockerfile_path"),
            app_port=ov.get("app_port"),
        )
        if draft.status == "needs_user_input":
            return draft
        if not draft.compose_file:
            return EnvActionResult(status="error", message="Failed to generate compose draft")
        compose_path = Path(draft.compose_file)

    # If we found an existing compose but it doesn't look like it exposes any HTTP service,
    # do not start it automatically (it may be infra-only).
    if compose_path is not None and generated_dir is None and not _compose_has_http_candidate(compose_path):
        rel = _safe_rel_path(repo_root, compose_path) or str(compose_path)
        return EnvActionResult(
            status="needs_user_input",
            message=(
                "A docker-compose file was found, but it does not appear to expose an HTTP service (no ports). "
                "Provide the correct compose file and/or indicate which service is the API."
            ),
            compose_file=str(compose_path),
            questions=[
                {
                    "id": "compose_path",
                    "question": "Which docker-compose file should be used to start the API environment?",
                    "options": [rel],
                },
                {
                    "id": "api_service",
                    "question": "Which compose service name is the API entrypoint?",
                    "expected_format": "string",
                },
                {
                    "id": "base_url",
                    "question": "What base URL should tests target after the environment starts?",
                    "expected_format": "url (e.g. http://localhost:8080)",
                },
            ],
        )

    if not compose_path.exists():
        return EnvActionResult(status="error", message="Compose file not found.", details={"compose": str(compose_path)})

    project_name = f"{cfg.docker_compose_project_prefix}_{job_id[:10]}_{uuid.uuid4().hex[:6]}"

    # Prefer docker compose (v2) invocation; configurable.
    cmd = _compose_cmd(cfg) + ["-p", project_name, "-f", str(compose_path), "up", "-d", "--remove-orphans"]
    rc, out, err = _run(cmd, cwd=repo_root if generated_dir is None else generated_dir, timeout=1800)

    if rc != 0:
        return EnvActionResult(
            status="error",
            message="docker compose up failed",
            compose_file=str(compose_path),
            project_name=project_name,
            details={"stdout": out, "stderr": err, "cmd": cmd},
        )

    detected_base_url = None
    if isinstance(run, dict) and isinstance(run.get("base_url"), str):
        detected_base_url = run.get("base_url")

    if not detected_base_url and isinstance(openapi_obj, dict):
        servers = openapi_obj.get("servers")
        if isinstance(servers, list) and servers and isinstance(servers[0], dict):
            detected_base_url = servers[0].get("url")

    return EnvActionResult(
        status="running",
        message="Environment started via docker compose.",
        compose_file=str(compose_path),
        project_name=project_name,
        detected_base_url=detected_base_url,
        details={"stdout": out, "stderr": err, "cmd": cmd},
    )


def stop_environment(*, cfg: Config, compose_file: str, project_name: str) -> EnvActionResult:
    if not cfg.allow_docker:
        return EnvActionResult(
            status="blocked",
            message="Docker execution is disabled. Set DISCOVERY_ALLOW_DOCKER=true to allow docker compose down.",
        )

    compose_path = Path(compose_file)
    if not compose_path.exists():
        return EnvActionResult(status="error", message="Compose file not found.", details={"compose": compose_file})

    cmd = _compose_cmd(cfg) + ["-p", project_name, "-f", str(compose_path), "down", "-v", "--remove-orphans"]
    rc, out, err = _run(cmd, cwd=compose_path.parent, timeout=900)

    if rc != 0:
        return EnvActionResult(
            status="error",
            message="docker compose down failed",
            compose_file=compose_file,
            project_name=project_name,
            details={"stdout": out, "stderr": err, "cmd": cmd},
        )

    return EnvActionResult(
        status="stopped",
        message="Environment stopped.",
        compose_file=compose_file,
        project_name=project_name,
        details={"stdout": out, "stderr": err, "cmd": cmd},
    )
