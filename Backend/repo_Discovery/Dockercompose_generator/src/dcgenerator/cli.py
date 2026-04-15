from __future__ import annotations

import json
import pathlib
import shlex
from typing import Optional

import typer

from .pipeline import MissingInputsError, generate_compose_for_target
from .git_ops import ensure_local_checkout
from .analyze import analyze_project

app = typer.Typer(add_completion=False, help="Generate docker-compose.yml for a project (optionally using Ollama).")


@app.callback()
def _root() -> None:
    """Project docker-compose generator."""
    return


@app.command("generate")
def generate(
    target: str = typer.Argument(..., help="Local path to a project (repo must already be cloned)."),
    branch: Optional[str] = typer.Option(
        None,
        help="(Unsupported) Kept for backward compatibility; clone/checkout must be done externally.",
        hidden=True,
    ),
    out: Optional[pathlib.Path] = typer.Option(
        None, "--out", help="Output directory. Default: repo folder (local) or clone folder (url)."
    ),
    in_place: bool = typer.Option(
        True,
        "--in-place/--no-in-place",
        help="Write files into the analyzed project directory (default true).",
    ),
    write_dockerfiles_in_repo: bool = typer.Option(
        False,
        "--write-dockerfiles-in-repo/--no-write-dockerfiles-in-repo",
        help=(
            "Allow generating Dockerfiles inside service build contexts even when --no-in-place. "
            "(Required to build services that have no Dockerfile.)"
        ),
    ),
    use_ollama: bool = typer.Option(
        False, "--use-ollama/--no-ollama", help="Use Ollama if available (default false)."
    ),
    ollama_model: str = typer.Option("llama3.1", help="Ollama model name (if enabled)."),
    db: Optional[str] = typer.Option(
        None,
        help="Force DB type (postgres|mysql|mongo|redis). If omitted, detection is used.",
    ),
    port: Optional[int] = typer.Option(
        None,
        help="Force app port. If omitted, detection is used.",
    ),
    health_path: Optional[str] = typer.Option(
        None,
        help="Force HTTP health path (e.g. /health). If omitted, detection is used.",
    ),
    api_service: Optional[str] = typer.Option(
        None,
        "--api-service",
        help=(
            "Override which service is treated as the main API and exposed on the host. "
            "Accepts either a service folder name or its sanitized compose name."
        ),
    ),
    strict: bool = typer.Option(
        True,
        "--strict/--no-strict",
        help=(
            "Strict mode: do not generate non-runnable artifacts. If a runnable start command cannot be inferred, "
            "dcgen will prompt for it (or fail if non-interactive)."
        ),
    ),
    ask_missing_env: bool = typer.Option(
        True,
        "--ask-missing-env/--no-ask-missing-env",
        help="If required env vars are detected (e.g. HOST_PORT), prompt for them and write a .env file next to docker-compose.yml.",
    ),
    ask_port_conflicts: bool = typer.Option(
        True,
        "--ask-port-conflicts/--no-ask-port-conflicts",
        help="If multiple Java services share the same port, ask to override ports (uses Spring SERVER_PORT env).",
    ),
    ask_missing_db: bool = typer.Option(
        True,
        "--ask-missing-db/--no-ask-missing-db",
        help="If no DB is detected and --db was not provided, ask which DB to include in docker-compose.yml.",
    ),
):
    """Analyze a project and generate docker-compose.yml (+ Dockerfile if missing)."""

    # Pre-analyze to detect port conflicts and/or missing DB.
    service_port_overrides: dict[str, int] = {}
    chosen_db: Optional[str] = db

    need_pre_analysis = (ask_port_conflicts or ask_missing_db) and (chosen_db is None)
    # Even if db is provided, port conflict resolution still needs the service list.
    need_pre_analysis = need_pre_analysis or ask_port_conflicts

    analysis = None
    if need_pre_analysis:
        project_dir = ensure_local_checkout(target=target, branch=branch)
        analysis = analyze_project(
            project_dir,
            forced_db=chosen_db,
            forced_port=port,
            forced_health_path=health_path,
        )

    if ask_port_conflicts and analysis is not None:
        service_port_overrides = _maybe_resolve_java_port_conflicts(analysis.services)

    if ask_missing_db and chosen_db is None and analysis is not None:
        detected = sorted({getattr(s, "db", None) for s in analysis.services if getattr(s, "db", None)})
        if not detected:
            chosen_db = _maybe_prompt_for_db_type()

    service_start_cmd_overrides: dict[str, list[str]] = {}
    service_extra_env_overrides: dict[str, dict[str, str]] = {}

    result = None
    last_missing: list[dict[str, str]] = []
    for _round in range(10):
        try:
            result = generate_compose_for_target(
                target=target,
                branch=branch,
                out_dir=out,
                in_place=in_place,
                use_ollama=use_ollama,
                ollama_model=ollama_model,
                forced_db=chosen_db,
                forced_port=port,
                forced_health_path=health_path,
                api_service=api_service,
                strict=strict,
                service_start_cmd_overrides=service_start_cmd_overrides or None,
                service_extra_env_overrides=service_extra_env_overrides or None,
                service_port_overrides=service_port_overrides or None,
                write_dockerfiles_in_repo=write_dockerfiles_in_repo,
            )
            break
        except MissingInputsError as exc:
            last_missing = list(getattr(exc, "missing", []) or [])
            if not strict:
                raise
            if not _maybe_prompt_for_missing_inputs(
                missing=last_missing,
                service_start_cmd_overrides=service_start_cmd_overrides,
            ):
                typer.echo("\nStrict generation cannot proceed without additional inputs:")
                for item in last_missing:
                    svc = item.get("service") or "(unknown)"
                    reason = item.get("reason") or "(no reason)"
                    typer.echo(f"- {svc}: {reason}")
                typer.echo(
                    "\nHint: add a Dockerfile to the service build context, re-run with --in-place, "
                    "or use --no-strict for a best-effort draft."
                )
                raise typer.Exit(code=2)

    if result is None:
        typer.echo("Too many strict-mode missing-input rounds; aborting.")
        for item in last_missing:
            svc = item.get("service") or "(unknown)"
            reason = item.get("reason") or "(no reason)"
            typer.echo(f"- {svc}: {reason}")
        raise typer.Exit(code=2)

    typer.echo(f"Project dir: {result.project_dir}")
    typer.echo(f"Wrote: {result.compose_path}")
    if result.dockerfile_paths:
        if len(result.dockerfile_paths) == 1:
            typer.echo(f"Wrote: {result.dockerfile_paths[0]}")
        else:
            typer.echo(f"Wrote: {len(result.dockerfile_paths)} Dockerfiles")

    if ask_missing_env and result.env_example_path is not None:
        env_path = result.compose_path.parent / ".env"
        _maybe_prompt_and_write_env(env_example_path=result.env_example_path, env_path=env_path)
        if env_path.exists():
            typer.echo(f"Wrote: {env_path}")

    if result.notes:
        typer.echo("\nNotes:")
        for n in result.notes:
            typer.echo(f"- {n}")


def _maybe_prompt_for_missing_inputs(
    *,
    missing: list[dict[str, str]],
    service_start_cmd_overrides: dict[str, list[str]],
) -> bool:
    """Try to satisfy MissingInputsError items via interactive prompts.

    Returns True if any overrides were collected and the caller should retry.
    """

    if not missing:
        return False

    start_cmd_items: list[dict[str, str]] = []
    for item in missing:
        reason = (item.get("reason") or "").lower()
        if "start command" in reason or "service_start_cmd_overrides" in reason:
            start_cmd_items.append(item)

    if not start_cmd_items:
        return False

    typer.echo("\nSome services need an explicit start command to generate a runnable Dockerfile.")
    typer.echo("Enter either a shell-style command or a JSON array, e.g.:")
    typer.echo("  - npm start")
    typer.echo("  - [\"npm\", \"start\"]")

    changed = False
    for item in start_cmd_items:
        service = (item.get("service") or "").strip()
        if not service:
            continue
        if service in service_start_cmd_overrides:
            continue

        typer.echo(f"\nService: {service}")
        if item.get("reason"):
            typer.echo(f"Reason: {item['reason']}")

        while True:
            raw = typer.prompt("Start command", default="", show_default=False)
            cmd = _parse_start_cmd_input(raw)
            if cmd:
                service_start_cmd_overrides[service] = cmd
                changed = True
                break
            typer.echo("Start command cannot be empty")

    return changed


def _parse_start_cmd_input(raw: str) -> list[str]:
    raw = (raw or "").strip()
    if not raw:
        return []

    if raw.startswith("["):
        try:
            parsed = json.loads(raw)
        except Exception as exc:  # noqa: BLE001
            raise typer.BadParameter("Start command JSON is invalid") from exc
        if not isinstance(parsed, list) or not parsed or not all(isinstance(x, str) and x.strip() for x in parsed):
            raise typer.BadParameter("Start command JSON must be a non-empty array of strings")
        return [x.strip() for x in parsed]

    try:
        parts = shlex.split(raw)
    except Exception as exc:  # noqa: BLE001
        raise typer.BadParameter("Start command could not be parsed (check quotes)") from exc
    return [p for p in parts if p.strip()]


def _maybe_prompt_and_write_env(*, env_example_path: pathlib.Path, env_path: pathlib.Path) -> None:
    if not env_example_path.exists():
        return

    required_vars: list[str] = []
    for line in env_example_path.read_text(encoding="utf-8", errors="ignore").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        k, _ = line.split("=", 1)
        k = k.strip()
        if k:
            required_vars.append(k)

    if not required_vars:
        return

    if env_path.exists():
        overwrite = typer.confirm(f"{env_path} already exists. Overwrite?", default=False)
        if not overwrite:
            return

    typer.echo("\nMissing required environment variables detected.")
    typer.echo("Please enter values to write a runnable .env file:")

    values: dict[str, str] = {}
    host_port_vars = [k for k in required_vars if k.upper().endswith("_HOST_PORT")]
    if len(host_port_vars) == len(required_vars) and len(required_vars) >= 3:
        if typer.confirm(
            f"{len(required_vars)} host ports are required. Auto-assign sequential ports?",
            default=True,
        ):
            base = typer.prompt("Base host port", default=8100, show_default=True)
            try:
                base_int = int(base)
            except Exception:
                raise typer.BadParameter("Base host port must be an integer")
            if not (1024 <= base_int <= 65535):
                raise typer.BadParameter("Base host port must be between 1024 and 65535")
            if base_int + len(required_vars) - 1 > 65535:
                raise typer.BadParameter("Base host port too high for the number of variables")

            for i, k in enumerate(required_vars):
                values[k] = str(base_int + i)
        else:
            used_ports: set[int] = set()
            for k in required_vars:
                if k.upper().endswith("_HOST_PORT"):
                    while True:
                        v = typer.prompt(k, default="", show_default=False)
                        try:
                            n = int(v)
                        except Exception:
                            typer.echo("Invalid port: must be an integer")
                            continue
                        if not (1024 <= n <= 65535):
                            typer.echo("Invalid port: must be between 1024 and 65535")
                            continue
                        if n in used_ports:
                            typer.echo("Invalid port: already used by another service")
                            continue
                        used_ports.add(n)
                        values[k] = str(n)
                        break
                else:
                    values[k] = typer.prompt(k, default="", show_default=False)
    else:
        used_ports: set[int] = set()
        for k in required_vars:
            if k.upper().endswith("_HOST_PORT"):
                while True:
                    v = typer.prompt(k, default="", show_default=False)
                    try:
                        n = int(v)
                    except Exception:
                        typer.echo("Invalid port: must be an integer")
                        continue
                    if not (1024 <= n <= 65535):
                        typer.echo("Invalid port: must be between 1024 and 65535")
                        continue
                    if n in used_ports:
                        typer.echo("Invalid port: already used by another service")
                        continue
                    used_ports.add(n)
                    values[k] = str(n)
                    break
            else:
                hide = any(tok in k.upper() for tok in ["PASSWORD", "SECRET", "TOKEN", "KEY"]) and "HOST_PORT" not in k.upper()
                values[k] = typer.prompt(k, default="", show_default=False, hide_input=hide)

    lines = ["# Generated by dcgen from required placeholders", ""]
    for k in required_vars:
        lines.append(f"{k}={values.get(k, '')}")
    lines.append("")
    env_path.write_text("\n".join(lines), encoding="utf-8")


def _maybe_resolve_java_port_conflicts(services: list[object]) -> dict[str, int]:
    # services is a list of ServiceAnalysis; keep signature loose to avoid import cycles.
    by_port: dict[int, list[str]] = {}
    for s in services:
        try:
            runtime = getattr(s, "runtime", None)
            name = getattr(s, "name", None)
            port = getattr(s, "port", None)
        except Exception:
            continue
        if runtime != "java":
            continue
        if not isinstance(port, int):
            continue
        if not name:
            continue
        by_port.setdefault(port, []).append(str(name))

    conflicts = {p: names for p, names in by_port.items() if len(names) > 1}
    if not conflicts:
        return {}

    # If ports are actually set to the same value in code, the only way to run them together
    # is to override at runtime. For Spring Boot this is done via SERVER_PORT.
    typer.echo("\nPort conflict detected among Java services:")
    for p, names in sorted(conflicts.items()):
        typer.echo(f"- port {p}: {', '.join(names)}")

    if not typer.confirm(
        "Do you want to override these services to use distinct ports (via SERVER_PORT env)?",
        default=True,
    ):
        return {}

    # Flatten in a stable order
    names_flat: list[str] = []
    for p in sorted(conflicts.keys()):
        names_flat.extend(sorted(conflicts[p]))

    overrides: dict[str, int] = {}
    if typer.confirm(
        f"Auto-assign distinct ports for {len(names_flat)} services?",
        default=True,
    ):
        base = typer.prompt("Base container port", default=9000, show_default=True)
        try:
            base_int = int(base)
        except Exception:
            raise typer.BadParameter("Base container port must be an integer")
        if not (1024 <= base_int <= 65535):
            raise typer.BadParameter("Base container port must be between 1024 and 65535")
        if base_int + len(names_flat) - 1 > 65535:
            raise typer.BadParameter("Base container port too high for the number of services")

        for i, name in enumerate(names_flat):
            v = base_int + i
            if v > 65535:
                raise typer.BadParameter("Base port too high; ran out of port range")
            overrides[name] = v
        return overrides

    used: set[int] = set()
    for name in names_flat:
        while True:
            v = typer.prompt(f"Port for {name}")
            try:
                vv = int(v)
            except Exception:
                typer.echo("Invalid port: must be an integer")
                continue
            if not (1024 <= vv <= 65535):
                typer.echo("Invalid port: must be between 1024 and 65535")
                continue
            if vv in used:
                typer.echo("Invalid port: already used by another service")
                continue
            used.add(vv)
            overrides[name] = vv
            break
    return overrides


def _maybe_prompt_for_db_type() -> Optional[str]:
    typer.echo("\nDB could not be inferred from the repo.")
    typer.echo("Choose a DB to include in docker-compose.yml (or 'none'): ")
    raw = typer.prompt("DB type", default="none", show_default=True).strip().lower()
    if raw in {"none", "no", "n", ""}:
        return None
    if raw in {"postgres", "postgresql", "pg"}:
        return "postgres"
    if raw in {"mysql"}:
        return "mysql"
    if raw in {"mongo", "mongodb"}:
        return "mongo"
    if raw in {"redis"}:
        return "redis"
    raise typer.BadParameter("DB type must be one of: mysql, postgres, mongo, redis, none")


@app.command("serve")
def serve(
    host: str = typer.Option("127.0.0.1", help="Bind host"),
    port: int = typer.Option(8001, help="Bind port"),
):
    """Start HTTP API server (for Postman): GET /health, POST /generate."""
    try:
        import uvicorn

        from .api_server import app as fastapi_app
    except Exception as e:  # pragma: no cover
        raise typer.BadParameter(
            "API dependencies missing. Reinstall with project deps (fastapi, uvicorn)."
        ) from e

    uvicorn.run(fastapi_app, host=host, port=port)


@app.command("inspect")
def inspect(
    target: str = typer.Argument(..., help="Local path to a project (repo must already be cloned)."),
    branch: Optional[str] = typer.Option(
        None,
        help="(Unsupported) Kept for backward compatibility; clone/checkout must be done externally.",
        hidden=True,
    ),
    db: Optional[str] = typer.Option(None, help="Force DB type (postgres|mysql|mongo|redis)."),
    port: Optional[int] = typer.Option(None, help="Force app port (only if single service)."),
    health_path: Optional[str] = typer.Option(None, help="Force HTTP health path (only if single service)."),
):
    """Print detected services, ports, DB and health endpoints."""

    project_dir = ensure_local_checkout(target=target, branch=branch)
    analysis = analyze_project(
        project_dir,
        forced_db=db,
        forced_port=port,
        forced_health_path=health_path,
    )

    typer.echo(f"Project dir: {analysis.project_dir}")
    typer.echo(f"Services detected: {len(analysis.services)}")
    for s in analysis.services:
        typer.echo(
            f"- {s.name}: kind={s.project_kind} runtime={s.runtime} dir={s.service_dir} port={s.port} db={s.db} health={s.health_path}"
        )


def main() -> None:
    app()


if __name__ == "__main__":
    main()
