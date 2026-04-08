from __future__ import annotations

import pathlib
import uuid
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
import typer

from .pipeline import generate_compose_for_target
from .git_ops import ensure_local_checkout
from .analyze import analyze_project

app = FastAPI(title="dcgen", version="0.1.0")
cli = typer.Typer(add_completion=False, help="Start dcgen HTTP API server.")


class GenerateRequest(BaseModel):
    target: str = Field(..., description="Local path to a project (repo must already be cloned)")
    branch: Optional[str] = Field(
        default=None,
        description="(Unsupported) Kept for backward compatibility; clone/checkout must be done externally.",
    )
    in_place: bool = Field(False, description="If true, write docker-compose.yml/Dockerfiles into the repo itself")
    write_dockerfiles_in_repo: bool = Field(
        False,
        description=(
            "If true, allow generating Dockerfiles inside the target repo build contexts even when in_place=false. "
            "Useful for local paths when you want the compose in the API run folder."
        ),
    )
    use_ollama: bool = False
    ollama_model: str = "llama3.1"
    db: Optional[str] = Field(None, description="postgres|mysql|mongo|redis")
    port: Optional[int] = None
    health_path: Optional[str] = None
    env_values: dict[str, str] = Field(
        default_factory=dict,
        description=(
            "Optional environment variables to write into the generated .env file (alongside docker-compose.yml). "
            "Keys should match required_env_vars; values are treated as literal strings."
        ),
    )
    auto_assign_host_ports: bool = Field(
        False,
        description=(
            "If true and the compose requires only *_HOST_PORT variables, auto-assign sequential host ports and write .env"
        ),
    )
    fail_on_missing: bool = Field(
        False,
        description=(
            "If true, return HTTP 409 with structured missing info when DB/env vars are required. "
            "This is useful for GUI flows that want to collect inputs first."
        ),
    )
    host_port_base: int = Field(
        8100,
        description="Base host port used when auto_assign_host_ports=true (ports assigned sequentially).",
        ge=1,
        le=65535,
    )


class GenerateResponse(BaseModel):
    run_id: str
    project_dir: str
    output_dir: str
    compose_yml: str
    dockerfile: Optional[str]
    dockerfile_paths: list[str] = []
    detected_db_types: list[str] = []
    missing_db: bool = False
    db_options: list[str] = ["postgres", "mysql", "mongo", "redis"]
    env_example: Optional[str] = None
    env: Optional[str] = None
    required_env_vars: list[str] = []
    missing_env_vars: list[str] = []
    notes: list[str]


class InspectRequest(BaseModel):
    target: str = Field(..., description="Local path to a project (repo must already be cloned)")
    branch: Optional[str] = Field(
        default=None,
        description="(Unsupported) Kept for backward compatibility; clone/checkout must be done externally.",
    )
    db: Optional[str] = Field(None, description="Force DB type (postgres|mysql|mongo|redis)")
    port: Optional[int] = None
    health_path: Optional[str] = None


class ServiceInfo(BaseModel):
    name: str
    dir: str
    project_kind: str
    runtime: str
    port: Optional[int]
    db: Optional[str]
    health_path: Optional[str]
    has_dockerfile: bool


class InspectResponse(BaseModel):
    project_dir: str
    services: list[ServiceInfo]


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/generate", response_model=GenerateResponse)
def generate(req: GenerateRequest) -> GenerateResponse:
    run_id = str(uuid.uuid4())
    output_dir = pathlib.Path.cwd() / ".dcgen" / "api-runs" / run_id
    output_dir.mkdir(parents=True, exist_ok=True)

    try:
        result = generate_compose_for_target(
            target=req.target,
            branch=req.branch,
            out_dir=output_dir,
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

    compose_text = (result.compose_path).read_text(encoding="utf-8")
    docker_text = None
    if result.dockerfile_path is not None:
        docker_text = result.dockerfile_path.read_text(encoding="utf-8")

    dockerfile_paths = [str(p) for p in (result.dockerfile_paths or [])]

    env_example_text: Optional[str] = None
    env_text: Optional[str] = None
    required_vars: list[str] = []
    missing_vars: list[str] = []
    env_path = result.compose_path.parent / ".env"

    if result.env_example_path is not None and result.env_example_path.exists():
        env_example_text = result.env_example_path.read_text(encoding="utf-8", errors="ignore")
        required_vars = _extract_required_env_vars(env_example_text)
        missing_vars = list(required_vars)

        # If caller provided env_values, write them into .env and treat them as satisfied.
        if req.env_values:
            allowed = set(required_vars)
            provided = {k: v for k, v in req.env_values.items() if k in allowed and v is not None and str(v).strip() != ""}
            if provided:
                env_text = _render_env_from_values(required_vars, provided)
                env_path.write_text(env_text, encoding="utf-8")
                missing_vars = [k for k in missing_vars if k not in provided]

        if req.auto_assign_host_ports and missing_vars:
            # Only auto-assign if ALL required vars are host ports; otherwise we must not guess.
            if all(v.upper().endswith("_HOST_PORT") for v in missing_vars):
                auto_env_text = _render_env_with_sequential_ports(missing_vars, base=req.host_port_base)
                # If we already wrote some provided vars above, append the auto-assigned ports.
                if env_text:
                    env_text = env_text.rstrip() + "\n" + auto_env_text.lstrip()
                else:
                    env_text = auto_env_text
                env_path.write_text(env_text, encoding="utf-8")
                missing_vars = []
            else:
                # keep missing vars for the caller to provide
                pass

    if env_text is None and env_path.exists():
        # If something else created it (e.g. user runs CLI in same output dir), return it.
        env_text = env_path.read_text(encoding="utf-8", errors="ignore")

    missing_db = (req.db is None and not result.detected_db_types)
    if req.fail_on_missing and (missing_db or missing_vars):
        raise HTTPException(
            status_code=409,
            detail={
                "missing_db": missing_db,
                "db_options": ["postgres", "mysql", "mongo", "redis"],
                "detected_db_types": result.detected_db_types,
                "required_env_vars": required_vars,
                "missing_env_vars": missing_vars,
                "notes": result.notes,
            },
        )

    return GenerateResponse(
        run_id=run_id,
        project_dir=str(result.project_dir),
        output_dir=str(output_dir),
        compose_yml=compose_text,
        dockerfile=docker_text,
        dockerfile_paths=dockerfile_paths,
        detected_db_types=result.detected_db_types,
        missing_db=missing_db,
        env_example=env_example_text,
        env=env_text,
        required_env_vars=required_vars,
        missing_env_vars=missing_vars,
        notes=result.notes,
    )


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
    lines = ["# Generated by dcgen (auto-assigned host ports)", ""]
    for i, k in enumerate(vars_):
        port = base + i
        if port > 65535:
            raise ValueError("host_port_base too high; ran out of port range")
        lines.append(f"{k}={port}")
    lines.append("")
    return "\n".join(lines)


def _render_env_from_values(required_order: list[str], values: dict[str, str]) -> str:
    lines = ["# Generated by dcgen (provided values)", ""]
    for k in required_order:
        if k in values:
            lines.append(f"{k}={values[k]}")
    lines.append("")
    return "\n".join(lines)


@app.post("/inspect", response_model=InspectResponse)
def inspect(req: InspectRequest) -> InspectResponse:
    try:
        project_dir = ensure_local_checkout(target=req.target, branch=req.branch)
        analysis = analyze_project(
            project_dir,
            forced_db=req.db,
            forced_port=req.port,
            forced_health_path=req.health_path,
        )
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return InspectResponse(
        project_dir=str(analysis.project_dir),
        services=[
            ServiceInfo(
                name=s.name,
                dir=str(s.service_dir),
                project_kind=s.project_kind,
                runtime=s.runtime,
                port=s.port,
                db=s.db,
                health_path=s.health_path,
                has_dockerfile=s.has_dockerfile,
            )
            for s in analysis.services
        ],
    )


@cli.command("serve")
def serve(
    host: str = typer.Option("127.0.0.1", help="Bind host"),
    port: int = typer.Option(8001, help="Bind port"),
):
    import uvicorn

    uvicorn.run(app, host=host, port=port)


def main() -> None:
    cli()
