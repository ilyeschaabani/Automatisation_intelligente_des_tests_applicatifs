from __future__ import annotations

import getpass
import os
import shutil
import subprocess
import sys
import time
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Optional

from fastapi import FastAPI, HTTPException
from pydantic import AliasChoices, BaseModel, Field


class CloneRequest(BaseModel):
    # Keep this as a plain string so SSH URLs like git@github.com:org/repo.git work.
    repo_url: str = Field(..., min_length=1, description="Git repository URL")
    branch: str = Field(default="main", description="Branch to clone")


class CloneResponse(BaseModel):
    session_id: str
    repo_path: str
    branch: str


class PrepareRequest(BaseModel):
    # Accept both {repo_url: ...} and {target: ...} to match dcgen-style clients.
    repo_url: str = Field(
        ...,
        min_length=1,
        validation_alias=AliasChoices("repo_url", "target"),
        description="Git repository URL (alias: target)",
    )
    branch: str = Field(default="main", description="Branch to clone")

    in_place: bool = Field(
        default=True,
        description="If true, write docker-compose.yml/Dockerfiles into the cloned repo itself.",
    )
    write_dockerfiles_in_repo: bool = Field(
        default=True,
        description=(
            "If true, allow generating Dockerfiles inside service build contexts even when in_place=false. "
            "(Safe here because the repo is a temp clone.)"
        ),
    )

    db: Optional[str] = Field(
        default=None,
        description="Force DB type for compose (postgres|mysql|mongo|redis). If omitted, detection is used.",
    )
    port: Optional[int] = Field(default=None, description="Force app port (only for single-service repos).")
    health_path: Optional[str] = Field(default=None, description="Force HTTP health path (only for single-service).")
    use_ollama: bool = Field(default=False, description="Use Ollama refinement if available.")
    ollama_model: str = Field(default="llama3.1", description="Ollama model name (if enabled).")

    env_values: dict[str, str] = Field(
        default_factory=dict,
        description=(
            "Optional environment variables to write into the generated .env file (alongside docker-compose.yml). "
            "Keys should match required_env_vars; values are treated as literal strings."
        ),
    )
    auto_assign_host_ports: bool = Field(
        default=True,
        description=(
            "If true and the compose requires only *_HOST_PORT variables, auto-assign sequential host ports and write .env"
        ),
    )
    host_port_base: int = Field(
        default=8100,
        ge=1,
        le=65535,
        description="Base host port used when auto_assign_host_ports=true (ports assigned sequentially).",
    )
    fail_on_missing: bool = Field(
        default=False,
        description=(
            "If true, return HTTP 409 with structured missing info when DB/env vars are required. "
            "This is useful for GUI flows that want to collect inputs first."
        ),
    )

    interactive: bool = Field(
        default=False,
        description=(
            "If true and the server has a TTY, prompt in the server terminal for missing DB/env values and block the "
            "HTTP request until answered. Dev-only; falls back to missing_* response if no TTY."
        ),
    )

    generate_openapi: bool = Field(
        default=True,
        description="If true, run github-discovery on the same cloned repo and produce OpenAPI outputs.",
    )


class PrepareResponse(BaseModel):
    session_id: str
    repo_path: str
    branch: str
    output_dir: str
    compose_path: str
    compose_yml: str
    dockerfile_paths: list[str] = []
    env_example_path: Optional[str] = None
    env_example: Optional[str] = None
    env_path: Optional[str] = None
    env: Optional[str] = None
    required_env_vars: list[str] = []
    missing_env_vars: list[str] = []
    detected_db_types: list[str] = []
    missing_db: bool = False
    db_options: list[str] = ["postgres", "mysql", "mongo", "redis"]
    notes: list[str] = []

    # Optional github-discovery outputs
    openapi_path: Optional[str] = None
    openapi: Optional[dict] = None
    endpoints_path: Optional[str] = None
    endpoints: Optional[list[dict]] = None
    stats_path: Optional[str] = None
    discovery_stats: Optional[dict] = None


class ContinueRequest(BaseModel):
    # All optional overrides to re-run generation on an existing session checkout.
    in_place: bool = Field(
        default=True,
        description="If true, write docker-compose.yml/Dockerfiles into the cloned repo itself.",
    )
    write_dockerfiles_in_repo: bool = Field(
        default=True,
        description="If true, allow generating Dockerfiles inside service build contexts.",
    )
    db: Optional[str] = Field(default=None, description="postgres|mysql|mongo|redis")
    port: Optional[int] = None
    health_path: Optional[str] = None
    use_ollama: bool = False
    ollama_model: str = "llama3.1"
    env_values: dict[str, str] = Field(default_factory=dict)
    auto_assign_host_ports: bool = True
    host_port_base: int = Field(8100, ge=1, le=65535)
    fail_on_missing: bool = False

    interactive: bool = False

    generate_openapi: bool = True


def _extract_required_env_vars(env_example_text: str) -> list[str]:
    required: list[str] = []
    for line in env_example_text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        k, _ = line.split("=", 1)
        k = k.strip()
        if k:
            required.append(k)
    return required


def _render_env_with_sequential_ports(vars_: list[str], *, base: int) -> str:
    if base < 1024:
        raise ValueError("host_port_base must be >= 1024")
    if base + len(vars_) - 1 > 65535:
        raise ValueError("host_port_base too high for number of variables")
    lines = ["# Generated by clone_repo (auto-assigned host ports)", ""]
    for i, k in enumerate(vars_):
        port = base + i
        if port > 65535:
            raise ValueError("host_port_base too high; ran out of port range")
        lines.append(f"{k}={port}")
    lines.append("")
    return "\n".join(lines)


def _render_env_from_values(required_order: list[str], values: dict[str, str]) -> str:
    lines = ["# Generated by clone_repo (provided values)", ""]
    for k in required_order:
        if k in values:
            lines.append(f"{k}={values[k]}")
    lines.append("")
    return "\n".join(lines)


def _is_tty_interactive() -> bool:
    try:
        return bool(sys.stdin and sys.stdin.isatty())
    except Exception:
        return False


def _is_sensitive_env_var(name: str) -> bool:
    u = name.upper()
    return any(token in u for token in ["PASSWORD", "PASS", "SECRET", "TOKEN", "API_KEY", "KEY"])


def _prompt_db_choice(*, options: list[str]) -> Optional[str]:
    options_l = [o.strip().lower() for o in options if o.strip()]
    if not options_l:
        return None
    while True:
        raw = input(f"Select DB ({'/'.join(options_l)}) or leave blank to skip: ").strip().lower()
        if raw == "":
            return None
        if raw in options_l:
            return raw
        print(f"Invalid choice: {raw}")


def _prompt_env_values(*, names: list[str]) -> dict[str, str]:
    values: dict[str, str] = {}
    for k in names:
        if _is_sensitive_env_var(k):
            v = getpass.getpass(f"Enter value for {k} (hidden; blank to skip): ").strip()
        else:
            v = input(f"Enter value for {k} (blank to skip): ").strip()
        if v != "":
            values[k] = v
    return values


@dataclass
class CloneSession:
    session_id: str
    repo_url: str
    branch: str
    path: Path
    created_at: float


def _repo_root() -> Path:
    # repo_Discovery/clone_repo/app.py -> repo_Discovery
    return Path(__file__).resolve().parents[1]


def _base_workdir() -> Path:
    # All temp clones + outputs live here (inside repo_Discovery)
    return _repo_root() / ".workdir"


def _clone_base_dir() -> Path:
    return _base_workdir() / "clones"


def _outputs_base_dir() -> Path:
    return _base_workdir() / "outputs"


def _session_ttl_seconds() -> int:
    # Safe default; can be overridden by env without guessing magic values in code.
    # If set to 0 or negative, TTL cleanup is disabled.
    try:
        v = int((os.environ.get("CLONE_SESSION_TTL_SECONDS") or "3600").strip())
    except Exception:
        v = 3600
    return v


def _cleanup_expired_sessions() -> None:
    ttl = _session_ttl_seconds()
    if ttl <= 0:
        return
    now = time.time()
    for session in list(registry.all().values()):
        if now - session.created_at > ttl:
            try:
                registry.pop(session.session_id)
            except KeyError:
                pass
            shutil.rmtree(session.path, ignore_errors=True)


class SessionRegistry:
    def __init__(self) -> None:
        self._sessions: Dict[str, CloneSession] = {}

    def create(self, repo_url: str, branch: str, path: Path) -> CloneSession:
        session_id = uuid.uuid4().hex
        session = CloneSession(
            session_id=session_id,
            repo_url=repo_url,
            branch=branch,
            path=path,
            created_at=time.time(),
        )
        self._sessions[session_id] = session
        return session

    def get(self, session_id: str) -> CloneSession:
        session = self._sessions.get(session_id)
        if session is None:
            raise KeyError(session_id)
        return session

    def pop(self, session_id: str) -> CloneSession:
        session = self._sessions.pop(session_id, None)
        if session is None:
            raise KeyError(session_id)
        return session

    def all(self) -> Dict[str, CloneSession]:
        return dict(self._sessions)


def _require_git_available() -> None:
    try:
        subprocess.run(["git", "--version"], check=True, capture_output=True, text=True)
    except Exception as exc:  # noqa: BLE001
        raise RuntimeError("git is not available on PATH") from exc


def _run_git_clone(repo_url: str, branch: str, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)

    cmd = [
        "git",
        "clone",
        "--branch",
        branch,
        "--single-branch",
        repo_url,
        str(destination),
    ]

    completed = subprocess.run(cmd, capture_output=True, text=True)
    if completed.returncode != 0:
        stderr = (completed.stderr or "").strip()
        stdout = (completed.stdout or "").strip()
        details = "\n".join([line for line in [stderr, stdout] if line])
        raise RuntimeError(details or "git clone failed")


registry = SessionRegistry()
app = FastAPI(title="Repo Discovery Orchestrator")


@app.on_event("startup")
def _startup() -> None:
    _require_git_available()


@app.post("/clone", response_model=CloneResponse)
def clone_repo(req: CloneRequest) -> CloneResponse:
    _cleanup_expired_sessions()
    base_tmp = _clone_base_dir()
    unique_folder = f"repo-{uuid.uuid4().hex}"
    repo_path = base_tmp / unique_folder

    try:
        _run_git_clone(str(req.repo_url), req.branch, repo_path)
    except Exception as exc:  # noqa: BLE001
        if repo_path.exists():
            shutil.rmtree(repo_path, ignore_errors=True)
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    session = registry.create(repo_url=str(req.repo_url), branch=req.branch, path=repo_path)
    return CloneResponse(session_id=session.session_id, repo_path=str(session.path), branch=session.branch)


@app.post("/prepare", response_model=PrepareResponse)
def prepare(req: PrepareRequest) -> PrepareResponse:
    """Clone a git repo into a temp folder, generate Docker artifacts in-place, and return them.

    This orchestrates two roles:
    - clone_repo: clone + lifecycle/cleanup of a temp checkout
    - dcgenerator: analyze local folder and generate docker-compose.yml (+ Dockerfiles if missing)
    """

    # Lazy import so the service can still start even if the generator isn't installed.
    # Try normal import first, then a mono-repo fallback (Dockercompose_generator/src).
    try:
        from dcgenerator.pipeline import generate_compose_for_target
    except Exception:  # noqa: BLE001
        repo_root = Path(__file__).resolve().parents[1]
        dcgen_src = repo_root / "Dockercompose_generator" / "src"
        if dcgen_src.exists():
            sys.path.insert(0, str(dcgen_src))
        try:
            from dcgenerator.pipeline import generate_compose_for_target
        except Exception as exc:  # noqa: BLE001
            raise HTTPException(
                status_code=500,
                detail=(
                    "dockercompose_generator (dcgenerator) is not available. "
                    "Install the package or run from the mono-repo so Dockercompose_generator/src is present."
                ),
            ) from exc

    _cleanup_expired_sessions()
    base_tmp = _clone_base_dir()
    unique_folder = f"repo-{uuid.uuid4().hex}"
    repo_path = base_tmp / unique_folder

    session: Optional[CloneSession] = None
    try:
        _run_git_clone(str(req.repo_url), req.branch, repo_path)
        session = registry.create(repo_url=str(req.repo_url), branch=req.branch, path=repo_path)

        run_id = uuid.uuid4().hex
        output_dir = repo_path if req.in_place else (_outputs_base_dir() / session.session_id / "dcgen" / run_id)
        output_dir.mkdir(parents=True, exist_ok=True)

        notes: list[str] = []

        def _postprocess(
            generated,
        ) -> tuple[str, Optional[str], Optional[str], list[str], list[str], Optional[str], Path, bool]:
            compose_text = generated.compose_path.read_text(encoding="utf-8", errors="ignore")

            env_example_text: Optional[str] = None
            env_text: Optional[str] = None
            required_vars: list[str] = []
            missing_vars: list[str] = []
            env_example_path: Optional[str] = None
            env_path: Path = generated.compose_path.parent / ".env"

            if generated.env_example_path is not None and generated.env_example_path.exists():
                env_example_path = str(generated.env_example_path)
                env_example_text = generated.env_example_path.read_text(encoding="utf-8", errors="ignore")
                required_vars = _extract_required_env_vars(env_example_text)
                missing_vars = list(required_vars)

                if req.env_values:
                    allowed = set(required_vars)
                    provided = {
                        k: v
                        for k, v in req.env_values.items()
                        if k in allowed and v is not None and str(v).strip() != ""
                    }
                    if provided:
                        env_text = _render_env_from_values(required_vars, provided)
                        env_path.write_text(env_text, encoding="utf-8")
                        missing_vars = [k for k in missing_vars if k not in provided]

                if req.auto_assign_host_ports and missing_vars:
                    # Only auto-assign if ALL required vars are host ports; otherwise we must not guess.
                    if all(v.upper().endswith("_HOST_PORT") for v in missing_vars):
                        auto_env_text = _render_env_with_sequential_ports(missing_vars, base=req.host_port_base)
                        if env_text:
                            env_text = env_text.rstrip() + "\n" + auto_env_text.lstrip()
                        else:
                            env_text = auto_env_text
                        env_path.write_text(env_text, encoding="utf-8")
                        missing_vars = []

            if env_text is None and env_path.exists():
                env_text = env_path.read_text(encoding="utf-8", errors="ignore")

            missing_db = (req.db is None and not generated.detected_db_types)
            return (
                compose_text,
                env_example_text,
                env_text,
                required_vars,
                missing_vars,
                env_example_path,
                env_path,
                missing_db,
            )

        result = generate_compose_for_target(
            target=str(repo_path),
            branch=None,
            out_dir=None if req.in_place else output_dir,
            in_place=req.in_place,
            use_ollama=req.use_ollama,
            ollama_model=req.ollama_model,
            forced_db=req.db,
            forced_port=req.port,
            forced_health_path=req.health_path,
            service_port_overrides=None,
            write_dockerfiles_in_repo=req.write_dockerfiles_in_repo,
        )
        notes = list(result.notes or [])

        (
            compose_text,
            env_example_text,
            env_text,
            required_vars,
            missing_vars,
            env_example_path,
            env_path,
            missing_db,
        ) = _postprocess(result)

        if req.interactive and (missing_db or missing_vars):
            if not _is_tty_interactive():
                notes.append("interactive=true requested, but server stdin is not a TTY; cannot prompt")
            else:
                if missing_db and req.db is None:
                    notes.append("Prompting for missing DB selection (interactive mode)")
                    chosen = _prompt_db_choice(options=["postgres", "mysql", "mongo", "redis"])
                    if chosen:
                        req.db = chosen
                        result = generate_compose_for_target(
                            target=str(repo_path),
                            branch=None,
                            out_dir=None if req.in_place else output_dir,
                            in_place=req.in_place,
                            use_ollama=req.use_ollama,
                            ollama_model=req.ollama_model,
                            forced_db=req.db,
                            forced_port=req.port,
                            forced_health_path=req.health_path,
                            service_port_overrides=None,
                            write_dockerfiles_in_repo=req.write_dockerfiles_in_repo,
                        )
                        notes = list(result.notes or []) + notes
                        (
                            compose_text,
                            env_example_text,
                            env_text,
                            required_vars,
                            missing_vars,
                            env_example_path,
                            env_path,
                            missing_db,
                        ) = _postprocess(result)

                if missing_vars:
                    notes.append("Prompting for missing env vars (interactive mode)")
                    provided = _prompt_env_values(names=missing_vars)
                    if provided:
                        if req.env_values:
                            req.env_values.update(provided)
                        else:
                            req.env_values = dict(provided)
                        env_text = _render_env_from_values(required_vars, req.env_values)
                        env_path.write_text(env_text, encoding="utf-8")
                        missing_vars = [k for k in missing_vars if k not in provided]
        if req.fail_on_missing and (missing_db or missing_vars):
            # Standby mode: keep the checkout + session so the caller can continue later.
            # Return session_id in the 409 payload.
            raise HTTPException(
                status_code=409,
                detail={
                    "session_id": session.session_id,
                    "repo_path": str(session.path),
                    "branch": session.branch,
                    "output_dir": str(output_dir),
                    "compose_path": str(result.compose_path),
                    "missing_db": missing_db,
                    "db_options": ["postgres", "mysql", "mongo", "redis"],
                    "detected_db_types": result.detected_db_types,
                    "required_env_vars": required_vars,
                    "missing_env_vars": missing_vars,
                    "env_example_path": env_example_path,
                    "notes": notes,
                },
            )

        openapi_payload: dict | None = None
        endpoints_payload: list[dict] | None = None
        discovery_stats: dict | None = None
        openapi_path: str | None = None
        endpoints_path: str | None = None
        stats_path: str | None = None

        if req.generate_openapi:
            try:
                openapi_payload, endpoints_payload, discovery_stats, openapi_path, endpoints_path, stats_path = (
                    _run_github_discovery(
                        session_id=session.session_id,
                        repo_path=repo_path,
                        use_ollama=req.use_ollama,
                        ollama_model=req.ollama_model,
                    )
                )
            except Exception as exc:  # noqa: BLE001
                # Keep docker artifacts even if discovery fails.
                notes.append(f"github-discovery failed: {exc}")

        return PrepareResponse(
            session_id=session.session_id,
            repo_path=str(session.path),
            branch=session.branch,
            output_dir=str(output_dir),
            compose_path=str(result.compose_path),
            compose_yml=compose_text,
            dockerfile_paths=[str(p) for p in (result.dockerfile_paths or [])],
            env_example_path=env_example_path,
            env_example=env_example_text,
            env_path=str(env_path) if (env_path is not None and env_path.exists()) else None,
            env=env_text,
            required_env_vars=required_vars,
            missing_env_vars=missing_vars,
            detected_db_types=result.detected_db_types,
            missing_db=missing_db,
            notes=notes,
            openapi_path=openapi_path,
            openapi=openapi_payload,
            endpoints_path=endpoints_path,
            endpoints=endpoints_payload,
            stats_path=stats_path,
            discovery_stats=discovery_stats,
        )

    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        # Cleanup failed runs aggressively to avoid leaking temp folders.
        if session is not None:
            try:
                registry.pop(session.session_id)
            except KeyError:
                pass
        if repo_path.exists():
            shutil.rmtree(repo_path, ignore_errors=True)
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/continue/{session_id}", response_model=PrepareResponse)
def continue_prepare(session_id: str, req: ContinueRequest) -> PrepareResponse:
    """Continue a previous /prepare that returned 409 (standby), by providing missing inputs."""

    try:
        session = registry.get(session_id)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Unknown session_id") from exc

    # Lazy import (same as /prepare)
    try:
        from dcgenerator.pipeline import generate_compose_for_target
    except Exception:  # noqa: BLE001
        repo_root = Path(__file__).resolve().parents[1]
        dcgen_src = repo_root / "Dockercompose_generator" / "src"
        if dcgen_src.exists():
            sys.path.insert(0, str(dcgen_src))
        try:
            from dcgenerator.pipeline import generate_compose_for_target
        except Exception as exc:  # noqa: BLE001
            raise HTTPException(
                status_code=500,
                detail="dockercompose_generator (dcgenerator) is not available.",
            ) from exc

    repo_path = session.path
    if not repo_path.exists():
        raise HTTPException(status_code=410, detail="Session repo folder no longer exists")

    _cleanup_expired_sessions()
    run_id = uuid.uuid4().hex
    output_dir = repo_path if req.in_place else (_outputs_base_dir() / session.session_id / "dcgen" / run_id)
    output_dir.mkdir(parents=True, exist_ok=True)

    try:
        result = generate_compose_for_target(
            target=str(repo_path),
            branch=None,
            out_dir=None if req.in_place else output_dir,
            in_place=req.in_place,
            use_ollama=req.use_ollama,
            ollama_model=req.ollama_model,
            forced_db=req.db,
            forced_port=req.port,
            forced_health_path=req.health_path,
            service_port_overrides=None,
            write_dockerfiles_in_repo=req.write_dockerfiles_in_repo,
        )
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    notes: list[str] = list(result.notes or [])

    compose_text = result.compose_path.read_text(encoding="utf-8", errors="ignore")

    env_example_text: Optional[str] = None
    env_text: Optional[str] = None
    required_vars: list[str] = []
    missing_vars: list[str] = []
    env_example_path: Optional[str] = None
    env_path: Path = result.compose_path.parent / ".env"

    if result.env_example_path is not None and result.env_example_path.exists():
        env_example_path = str(result.env_example_path)
        env_example_text = result.env_example_path.read_text(encoding="utf-8", errors="ignore")
        required_vars = _extract_required_env_vars(env_example_text)
        missing_vars = list(required_vars)

        if req.env_values:
            allowed = set(required_vars)
            provided = {
                k: v
                for k, v in req.env_values.items()
                if k in allowed and v is not None and str(v).strip() != ""
            }
            if provided:
                env_text = _render_env_from_values(required_vars, provided)
                env_path.write_text(env_text, encoding="utf-8")
                missing_vars = [k for k in missing_vars if k not in provided]

        if req.auto_assign_host_ports and missing_vars:
            if all(v.upper().endswith("_HOST_PORT") for v in missing_vars):
                auto_env_text = _render_env_with_sequential_ports(missing_vars, base=req.host_port_base)
                if env_text:
                    env_text = env_text.rstrip() + "\n" + auto_env_text.lstrip()
                else:
                    env_text = auto_env_text
                env_path.write_text(env_text, encoding="utf-8")
                missing_vars = []

    if env_text is None and env_path.exists():
        env_text = env_path.read_text(encoding="utf-8", errors="ignore")

    missing_db = (req.db is None and not result.detected_db_types)

    if req.interactive and (missing_db or missing_vars):
        if not _is_tty_interactive():
            notes.append("interactive=true requested, but server stdin is not a TTY; cannot prompt")
        else:
            if missing_db and req.db is None:
                notes.append("Prompting for missing DB selection (interactive mode)")
                chosen = _prompt_db_choice(options=["postgres", "mysql", "mongo", "redis"])
                if chosen:
                    req.db = chosen
                    try:
                        result = generate_compose_for_target(
                            target=str(repo_path),
                            branch=None,
                            out_dir=None if req.in_place else output_dir,
                            in_place=req.in_place,
                            use_ollama=req.use_ollama,
                            ollama_model=req.ollama_model,
                            forced_db=req.db,
                            forced_port=req.port,
                            forced_health_path=req.health_path,
                            service_port_overrides=None,
                            write_dockerfiles_in_repo=req.write_dockerfiles_in_repo,
                        )
                    except Exception as exc:  # noqa: BLE001
                        raise HTTPException(status_code=400, detail=str(exc)) from exc

                    notes = list(result.notes or []) + notes
                    compose_text = result.compose_path.read_text(encoding="utf-8", errors="ignore")

                    env_example_text = None
                    env_text = None
                    required_vars = []
                    missing_vars = []
                    env_example_path = None
                    env_path = result.compose_path.parent / ".env"

                    if result.env_example_path is not None and result.env_example_path.exists():
                        env_example_path = str(result.env_example_path)
                        env_example_text = result.env_example_path.read_text(encoding="utf-8", errors="ignore")
                        required_vars = _extract_required_env_vars(env_example_text)
                        missing_vars = list(required_vars)

                        if req.env_values:
                            allowed = set(required_vars)
                            provided = {
                                k: v
                                for k, v in req.env_values.items()
                                if k in allowed and v is not None and str(v).strip() != ""
                            }
                            if provided:
                                env_text = _render_env_from_values(required_vars, provided)
                                env_path.write_text(env_text, encoding="utf-8")
                                missing_vars = [k for k in missing_vars if k not in provided]

                        if req.auto_assign_host_ports and missing_vars:
                            if all(v.upper().endswith("_HOST_PORT") for v in missing_vars):
                                auto_env_text = _render_env_with_sequential_ports(missing_vars, base=req.host_port_base)
                                if env_text:
                                    env_text = env_text.rstrip() + "\n" + auto_env_text.lstrip()
                                else:
                                    env_text = auto_env_text
                                env_path.write_text(env_text, encoding="utf-8")
                                missing_vars = []

                    if env_text is None and env_path.exists():
                        env_text = env_path.read_text(encoding="utf-8", errors="ignore")

                    missing_db = (req.db is None and not result.detected_db_types)

            if missing_vars:
                notes.append("Prompting for missing env vars (interactive mode)")
                provided = _prompt_env_values(names=missing_vars)
                if provided:
                    if req.env_values:
                        req.env_values.update(provided)
                    else:
                        req.env_values = dict(provided)
                    env_text = _render_env_from_values(required_vars, req.env_values)
                    env_path.write_text(env_text, encoding="utf-8")
                    missing_vars = [k for k in missing_vars if k not in provided]
    if req.fail_on_missing and (missing_db or missing_vars):
        raise HTTPException(
            status_code=409,
            detail={
                "session_id": session.session_id,
                "repo_path": str(session.path),
                "branch": session.branch,
                "output_dir": str(output_dir),
                "compose_path": str(result.compose_path),
                "missing_db": missing_db,
                "db_options": ["postgres", "mysql", "mongo", "redis"],
                "detected_db_types": result.detected_db_types,
                "required_env_vars": required_vars,
                "missing_env_vars": missing_vars,
                "env_example_path": env_example_path,
                "notes": notes,
            },
        )

    openapi_payload: dict | None = None
    endpoints_payload: list[dict] | None = None
    discovery_stats: dict | None = None
    openapi_path: str | None = None
    endpoints_path: str | None = None
    stats_path: str | None = None

    if req.generate_openapi:
        try:
            openapi_payload, endpoints_payload, discovery_stats, openapi_path, endpoints_path, stats_path = _run_github_discovery(
                session_id=session.session_id,
                repo_path=repo_path,
                use_ollama=req.use_ollama,
                ollama_model=req.ollama_model,
            )
        except Exception as exc:  # noqa: BLE001
            notes.append(f"github-discovery failed: {exc}")

    return PrepareResponse(
        session_id=session.session_id,
        repo_path=str(session.path),
        branch=session.branch,
        output_dir=str(output_dir),
        compose_path=str(result.compose_path),
        compose_yml=compose_text,
        dockerfile_paths=[str(p) for p in (result.dockerfile_paths or [])],
        env_example_path=env_example_path,
        env_example=env_example_text,
        env_path=str(env_path) if env_path.exists() else None,
        env=env_text,
        required_env_vars=required_vars,
        missing_env_vars=missing_vars,
        detected_db_types=result.detected_db_types,
        missing_db=missing_db,
        notes=notes,
        openapi_path=openapi_path,
        openapi=openapi_payload,
        endpoints_path=endpoints_path,
        endpoints=endpoints_payload,
        stats_path=stats_path,
        discovery_stats=discovery_stats,
    )


def _run_github_discovery(
    *,
    session_id: str,
    repo_path: Path,
    use_ollama: bool = False,
    ollama_model: str = "llama3.1",
) -> tuple[dict, list[dict], dict, str, str, str]:
    """Run github-discovery pipeline on an existing local checkout.

    Returns: (openapi_json, endpoints_json, stats_json, openapi_path, endpoints_path, stats_path)
    """

    repo_root = _repo_root()
    service_root = repo_root / "github-discovery"

    # Ensure the github-discovery package (named 'src') is importable.
    if service_root.exists():
        sys.path.insert(0, str(service_root))

    try:
        from src.config import Config  # type: ignore
        from src.pipeline import Pipeline  # type: ignore
    except Exception as exc:  # noqa: BLE001
        raise RuntimeError("github-discovery is not available (cannot import src.*)") from exc

    output_dir = _outputs_base_dir() / session_id / "github_discovery"
    output_dir.mkdir(parents=True, exist_ok=True)

    config = Config()
    config.output_dir = str(output_dir)
    # Reduce accidental repo writes outside our workdir (repos_dir used only when cloning).
    config.repos_dir = str(_base_workdir() / "github_discovery_repos")

    # Keep github-discovery focused on endpoint extraction by default.
    # OpenAPI can be enabled later via OUTPUT_OPENAPI=true if needed.
    try:
        config.output_openapi = False
    except Exception:
        pass

    if use_ollama:
        config.ollama_enabled = True
        config.ollama_model = ollama_model

    pipeline = Pipeline(config)

    # github-discovery package uses repo_path.name for output prefix; override with a stable name
    repo_name = f"session_{session_id}"
    result = pipeline.run_on_path(repo_path=repo_path, repo_url=str(repo_path), repo_name=repo_name)

    openapi_file = Path(config.output_dir) / f"{repo_name}_openapi.json"
    endpoints_file = Path(config.output_dir) / f"{repo_name}_endpoints.json"
    stats_file = Path(config.output_dir) / f"{repo_name}_stats.json"

    openapi_path = str(openapi_file) if openapi_file.exists() else None
    endpoints_path = str(endpoints_file) if endpoints_file.exists() else None
    stats_path = str(stats_file) if stats_file.exists() else None

    openapi_json = result.get("openapi") or {}
    endpoints_json = result.get("final_endpoints") or []
    stats_json = result.get("stats") or {}
    return openapi_json, endpoints_json, stats_json, openapi_path, endpoints_path, stats_path


@app.post("/done/{session_id}")
def done(session_id: str) -> dict:
    try:
        session = registry.pop(session_id)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Unknown session_id") from exc

    shutil.rmtree(session.path, ignore_errors=True)
    return {"ok": True, "deleted": str(session.path)}


@app.on_event("shutdown")
def _shutdown() -> None:
    # Best-effort cleanup to avoid leaving tmp dirs behind if the service is stopped.
    for session in list(registry.all().values()):
        shutil.rmtree(session.path, ignore_errors=True)
        try:
            registry.pop(session.session_id)
        except KeyError:
            pass
