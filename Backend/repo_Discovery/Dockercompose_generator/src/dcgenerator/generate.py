from __future__ import annotations

import dataclasses
import pathlib
from typing import Any, Optional

import yaml
import re


@dataclasses.dataclass(frozen=True)
class DockerfilePlan:
    runtime: str
    port: int
    start_cmd: Optional[list[str]] = None
    extra_env: Optional[dict[str, str]] = None
    java_build_tool: Optional[str] = None  # 'maven' | 'gradle'
    java_version: Optional[int] = None


@dataclasses.dataclass(frozen=True)
class ServiceArtifacts:
    name: str
    service_dir: pathlib.Path
    project_kind: str
    runtime: str
    port: Optional[int]
    db: Optional[str]
    health_path: Optional[str]
    dockerfile: Optional[DockerfilePlan]


@dataclasses.dataclass(frozen=True)
class ComposeArtifacts:
    project_dir: pathlib.Path
    services: list[ServiceArtifacts]


@dataclasses.dataclass(frozen=True)
class WriteResult:
    compose_path: pathlib.Path
    dockerfile_paths: list[pathlib.Path]
    env_example_path: Optional[pathlib.Path]
    notes: list[str]
    api_service: Optional[str] = None


def render_and_write_artifacts(
    *,
    output_dir: pathlib.Path,
    analysis: Any,
    artifacts: ComposeArtifacts,
    can_write_dockerfiles: bool,
    api_service: Optional[str] = None,
) -> WriteResult:
    notes: list[str] = []

    api_original_name = _resolve_api_service_original_name(artifacts.services, override=api_service)
    service_name_by_original = _make_compose_service_name_map(artifacts.services)
    api_compose_name = service_name_by_original.get(api_original_name, _sanitize_compose_service_name(api_original_name))
    api_port = next((s.port for s in artifacts.services if s.name == api_original_name), None)
    if isinstance(api_port, int):
        notes.append(
            f"Main API service: {api_compose_name} (container port {api_port}, published as dynamic host port 0:{api_port})"
        )
    else:
        notes.append(f"Main API service: {api_compose_name}")

    compose = _build_compose_dict(artifacts, base_dir=output_dir, api_service=api_service)

    compose_path = output_dir / "docker-compose.yml"
    # Avoid line-wrapping long CMD arguments (healthchecks), which can be surprising in YAML.
    compose_text = yaml.safe_dump(compose, sort_keys=False, width=4096)
    compose_path.write_text(compose_text, encoding="utf-8")

    required_envs = _collect_required_env_vars(compose)
    env_example_path: Optional[pathlib.Path] = None
    if required_envs:
        env_example_path = output_dir / ".env.example"
        env_example_path.write_text(_render_env_example(required_envs), encoding="utf-8")
        notes.append("Wrote .env.example with required variables for this compose.")

    dockerfile_paths: list[pathlib.Path] = []
    if can_write_dockerfiles:
        for s in artifacts.services:
            if s.dockerfile is None:
                continue
            # Write into the service directory (non-invasive: only if Dockerfile absent was detected)
            df_path = s.service_dir / "Dockerfile"
            if not df_path.exists():
                df_path.write_text(_render_dockerfile(s.dockerfile), encoding="utf-8")
                dockerfile_paths.append(df_path)
        if dockerfile_paths:
            notes.append("Dockerfile(s) generated for services that did not contain one.")
    else:
        missing = [s.name for s in artifacts.services if s.dockerfile is not None]
        if missing:
            notes.append(
                "Some services are missing Dockerfile. Re-run with --in-place to allow generating Dockerfiles inside the repo."
            )

    dbs = {s.db for s in artifacts.services if s.db}
    if not dbs:
        notes.append("DB could not be inferred; compose contains only app services.")
    else:
        notes.append(
            "DB detected: compose uses required env vars (no guessed credentials). Provide them via .env or shell env."
        )

    if all(s.health_path is None for s in artifacts.services):
        notes.append("Health endpoint(s) could not be inferred; app healthchecks (if present) use TCP checks.")

    return WriteResult(
        compose_path=compose_path,
        dockerfile_paths=dockerfile_paths,
        env_example_path=env_example_path,
        notes=notes,
        api_service=api_compose_name,
    )


def render_compose_yaml_preview(*, artifacts: ComposeArtifacts, base_dir: pathlib.Path, api_service: Optional[str] = None) -> str:
    """Render a docker-compose.yml preview for LLM verification.

    This uses the same compose builder as the writer, but does not touch disk.
    """

    compose = _build_compose_dict(artifacts, base_dir=base_dir, api_service=api_service)
    return yaml.safe_dump(compose, sort_keys=False, width=4096)


def _sanitize_compose_service_name(name: str) -> str:
    """Return a docker-compose-compatible service name."""

    normalized = re.sub(r"[^a-z0-9-]+", "-", name.lower()).strip("-")
    return normalized or "service"


def _env_prefix_from_service_name(name: str) -> str:
    """Return an env-var-safe prefix derived from a service name."""

    normalized = re.sub(r"[^A-Z0-9]+", "_", name.upper()).strip("_")
    return normalized or "SERVICE"


def _build_compose_dict(a: ComposeArtifacts, *, base_dir: pathlib.Path, api_service: Optional[str] = None) -> dict[str, Any]:
    services: dict[str, Any] = {}
    volumes: dict[str, Any] = {}

    network_name = "pi-network"

    db_types = {s.db for s in a.services if s.db}
    db_service_names: dict[str, str] = {
        "postgres": "postgres",
        "mysql": "mysql",
        "mongo": "mongo",
        "redis": "redis",
    }

    service_name_by_original = _make_compose_service_name_map(a.services)

    service_names = [s.name for s in a.services]
    # Heuristic naming used in many Spring Cloud repos
    has_eureka = any(_sanitize_compose_service_name(n) in {"eurekaserver", "eureka-server"} for n in service_names)
    has_gateway = any(_sanitize_compose_service_name(n) in {"gateway", "api-gateway"} for n in service_names)
    eureka_name = next(
        (n for n in service_names if _sanitize_compose_service_name(n) in {"eurekaserver", "eureka-server"}),
        None,
    )
    gateway_name = next(
        (n for n in service_names if _sanitize_compose_service_name(n) in {"gateway", "api-gateway"}),
        None,
    )
    eureka_service_name = service_name_by_original.get(eureka_name) if eureka_name else None
    gateway_service_name = service_name_by_original.get(gateway_name) if gateway_name else None

    api_original_name = _resolve_api_service_original_name(a.services, override=api_service)
    api_service_name = service_name_by_original.get(api_original_name)

    for s in a.services:
        compose_service_name = service_name_by_original[s.name]
        context = _relpath(base_dir, s.service_dir)
        svc: dict[str, Any] = {
            "build": {"context": context},
            "environment": {},
            "networks": [network_name],
        }

        # Basic service startup order (matches common Spring Cloud setup)
        if has_eureka and eureka_service_name and s.name != eureka_name:
            # gateway and apps usually need eureka
            svc.setdefault("depends_on", {})[eureka_service_name] = {"condition": "service_started"}
        if has_gateway and gateway_service_name and s.name not in {gateway_name, eureka_name}:
            # apps often depend on gateway
            svc.setdefault("depends_on", {})[gateway_service_name] = {"condition": "service_started"}

        # If an explicit Dockerfile exists in the context, reference it (matches common compose style).
        try:
            if (s.service_dir / "Dockerfile").exists():
                svc["build"]["dockerfile"] = "Dockerfile"
        except Exception:
            pass

        # container port
        if s.port is not None:
            container_port = str(s.port)
            if s.runtime == "java":
                svc["environment"]["SERVER_PORT"] = container_port
            else:
                svc["environment"]["PORT"] = container_port

            # Expose only the main API service, and do it with a dynamic host port to allow
            # multiple runs in parallel without host port collisions.
            if api_service_name and compose_service_name == api_service_name:
                svc["ports"] = [f"0:{container_port}"]

        hc = _service_healthcheck(s.runtime, s.port, s.health_path)
        if hc is not None:
            svc["healthcheck"] = hc

        if s.db in db_service_names:
            db_svc_name = db_service_names[s.db]
            svc.setdefault("depends_on", {})[db_svc_name] = {"condition": "service_healthy"}
            svc["environment"].update(_db_env_for_app(s.db, host=db_svc_name, runtime=s.runtime))

        # Spring Cloud Eureka client configuration (only when eureka is present)
        if s.runtime == "java" and has_eureka and eureka_service_name and s.name != eureka_name:
            svc["environment"].setdefault(
                "EUREKA_CLIENT_SERVICEURL_DEFAULTZONE",
                f"http://{eureka_service_name}:8761/eureka",
            )

        services[compose_service_name] = svc

    for db in sorted([d for d in db_types if d is not None]):
        db_svc_name = db_service_names.get(db)
        if not db_svc_name:
            continue
        db_service, db_vols = _db_service(db, network_name=network_name)
        services[db_svc_name] = db_service
        volumes.update(db_vols)

    compose: dict[str, Any] = {"services": services}
    if volumes:
        compose["volumes"] = volumes
    compose["networks"] = {network_name: {"driver": "bridge"}}
    compose["version"] = "3"
    return compose


def _make_compose_service_name_map(services: list[ServiceArtifacts]) -> dict[str, str]:
    """Return stable, collision-free docker-compose service names for service artifacts."""

    db_service_names = {"postgres", "mysql", "mongo", "redis"}
    service_name_by_original: dict[str, str] = {}
    used_service_names: set[str] = set()

    for s in services:
        base_name = _sanitize_compose_service_name(s.name)
        candidate = base_name
        suffix = 2
        # Ensure generated app service names do not collide with each other or DB service names.
        while candidate in used_service_names or candidate in db_service_names:
            candidate = f"{base_name}-{suffix}"
            suffix += 1
        used_service_names.add(candidate)
        service_name_by_original[s.name] = candidate

    return service_name_by_original


def _resolve_api_service_original_name(services: list[ServiceArtifacts], *, override: Optional[str]) -> str:
    """Resolve an override to an existing service name, or choose a sensible default."""

    if override:
        override_norm = _sanitize_compose_service_name(str(override))
        for s in services:
            if s.name == override:
                return s.name
            if _sanitize_compose_service_name(s.name) == override_norm:
                return s.name

    return _choose_api_service_original_name(services)


def _choose_api_service_original_name(services: list[ServiceArtifacts]) -> str:
    """Pick the most likely main HTTP API service.

    This is a best-effort heuristic to decide which service should be exposed on the host.
    It should be stable/deterministic, and it must not require user input.
    """

    if not services:
        return "app"
    if len(services) == 1:
        return services[0].name

    def score(svc: ServiceArtifacts) -> tuple[int, int, str]:
        n = _sanitize_compose_service_name(svc.name)
        pts = 0

        # Strong indicators
        if n in {"gateway", "api-gateway"} or "gateway" in n:
            pts += 100
        if "api" in n:
            pts += 80
        if "backend" in n:
            pts += 70
        if "server" in n:
            pts += 30

        # Soft/default indicators
        if n in {"app", "service"}:
            pts += 10

        # Prefer runtimes we can reasonably treat as HTTP APIs.
        if svc.runtime in {"java", "node", "python"}:
            pts += 5

        # Tie-breakers: prefer having a known port, then stable name ordering.
        has_port = 1 if isinstance(svc.port, int) else 0
        return (pts, has_port, n)

    best = sorted(services, key=score, reverse=True)[0]
    return best.name


def _db_env_for_app(db: str, *, host: str, runtime: str) -> dict[str, str]:
    # For Spring Boot, prefer SPRING_* env vars (matches common compose setups).
    if runtime == "java":
        if db == "mysql":
            return {
                "MYSQL_DATABASE": "${MYSQL_DATABASE:?set MYSQL_DATABASE}",
                "MYSQL_USER": "${MYSQL_USER:?set MYSQL_USER}",
                "MYSQL_PASSWORD": "${MYSQL_PASSWORD:?set MYSQL_PASSWORD}",
                "SPRING_DATASOURCE_URL": f"jdbc:mysql://{host}:3306/${{MYSQL_DATABASE}}",
                "SPRING_DATASOURCE_USERNAME": "${MYSQL_USER}",
                "SPRING_DATASOURCE_PASSWORD": "${MYSQL_PASSWORD}",
            }
        if db == "postgres":
            return {
                "POSTGRES_DB": "${POSTGRES_DB:?set POSTGRES_DB}",
                "POSTGRES_USER": "${POSTGRES_USER:?set POSTGRES_USER}",
                "POSTGRES_PASSWORD": "${POSTGRES_PASSWORD:?set POSTGRES_PASSWORD}",
                "SPRING_DATASOURCE_URL": f"jdbc:postgresql://{host}:5432/${{POSTGRES_DB}}",
                "SPRING_DATASOURCE_USERNAME": "${POSTGRES_USER}",
                "SPRING_DATASOURCE_PASSWORD": "${POSTGRES_PASSWORD}",
            }
        if db == "mongo":
            return {
                "MONGO_INITDB_DATABASE": "${MONGO_INITDB_DATABASE:?set MONGO_INITDB_DATABASE}",
                "SPRING_DATA_MONGODB_URI": f"mongodb://{host}:27017/${{MONGO_INITDB_DATABASE}}",
            }
        if db == "redis":
            return {
                "SPRING_DATA_REDIS_HOST": host,
                "SPRING_DATA_REDIS_PORT": "6379",
            }

    if db == "postgres":
        return {
            "POSTGRES_DB": "${POSTGRES_DB:?set POSTGRES_DB}",
            "POSTGRES_USER": "${POSTGRES_USER:?set POSTGRES_USER}",
            "POSTGRES_PASSWORD": "${POSTGRES_PASSWORD:?set POSTGRES_PASSWORD}",
            "DATABASE_URL": f"postgresql://${{POSTGRES_USER}}:${{POSTGRES_PASSWORD}}@{host}:5432/${{POSTGRES_DB}}",
        }
    if db == "mysql":
        return {
            "MYSQL_DATABASE": "${MYSQL_DATABASE:?set MYSQL_DATABASE}",
            "MYSQL_USER": "${MYSQL_USER:?set MYSQL_USER}",
            "MYSQL_PASSWORD": "${MYSQL_PASSWORD:?set MYSQL_PASSWORD}",
            "DATABASE_URL": f"mysql://${{MYSQL_USER}}:${{MYSQL_PASSWORD}}@{host}:3306/${{MYSQL_DATABASE}}",
        }
    if db == "mongo":
        return {
            "MONGO_INITDB_DATABASE": "${MONGO_INITDB_DATABASE:?set MONGO_INITDB_DATABASE}",
            "MONGODB_URI": f"mongodb://{host}:27017/${{MONGO_INITDB_DATABASE}}",
        }
    if db == "redis":
        return {
            "REDIS_URL": f"redis://{host}:6379/0",
        }
    return {}


def _db_service(db: str, *, network_name: str) -> tuple[dict[str, Any], dict[str, Any]]:
    volumes: dict[str, Any] = {}

    if db == "postgres":
        svc = {
            "image": "postgres:16-alpine",
            "environment": {
                "POSTGRES_DB": "${POSTGRES_DB:?set POSTGRES_DB}",
                "POSTGRES_USER": "${POSTGRES_USER:?set POSTGRES_USER}",
                "POSTGRES_PASSWORD": "${POSTGRES_PASSWORD:?set POSTGRES_PASSWORD}",
            },
            "volumes": ["pgdata:/var/lib/postgresql/data"],
            "networks": [network_name],
            "healthcheck": {
                "test": ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"],
                "interval": "5s",
                "timeout": "3s",
                "retries": 20,
            },
        }
        volumes["pgdata"] = {}
        return svc, volumes

    if db == "mysql":
        svc = {
            "image": "mysql:8",
            "environment": {
                "MYSQL_DATABASE": "${MYSQL_DATABASE:?set MYSQL_DATABASE}",
                "MYSQL_USER": "${MYSQL_USER:?set MYSQL_USER}",
                "MYSQL_PASSWORD": "${MYSQL_PASSWORD:?set MYSQL_PASSWORD}",
                "MYSQL_ROOT_PASSWORD": "${MYSQL_ROOT_PASSWORD:?set MYSQL_ROOT_PASSWORD}",
            },
            "volumes": ["mysqldata:/var/lib/mysql"],
            "networks": [network_name],
            "healthcheck": {
                "test": ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -uroot -p$${MYSQL_ROOT_PASSWORD}"],
                "interval": "5s",
                "timeout": "5s",
                "retries": 30,
            },
        }
        volumes["mysqldata"] = {}
        return svc, volumes

    if db == "mongo":
        svc = {
            "image": "mongo:7",
            "environment": {
                "MONGO_INITDB_DATABASE": "${MONGO_INITDB_DATABASE:?set MONGO_INITDB_DATABASE}",
            },
            "volumes": ["mongodata:/data/db"],
            "networks": [network_name],
            "healthcheck": {
                "test": [
                    "CMD-SHELL",
                    "mongosh --quiet --eval 'db.adminCommand({ ping: 1 })' || exit 1",
                ],
                "interval": "5s",
                "timeout": "5s",
                "retries": 30,
            },
        }
        volumes["mongodata"] = {}
        return svc, volumes

    if db == "redis":
        svc = {
            "image": "redis:7-alpine",
            "networks": [network_name],
            "healthcheck": {
                "test": ["CMD", "redis-cli", "ping"],
                "interval": "5s",
                "timeout": "3s",
                "retries": 30,
            },
        }
        return svc, volumes

    return {"image": "alpine:3.20"}, volumes


def _service_healthcheck(runtime: str, port: Optional[int], health_path: Optional[str]) -> Optional[dict[str, Any]]:
    if runtime not in {"python", "node"}:
        return None
    if port is None:
        return None

    # If we know a health path, do an HTTP check using the runtime we know exists.
    if health_path:
        url = f"http://127.0.0.1:{port}{health_path}"
        if runtime == "node":
            js = (
                "const http=require('http');"
                f"const req=http.get('{url}',res=>{{process.exit(res.statusCode>=200 && res.statusCode<400?0:1)}});"
                "req.on('error',()=>process.exit(1));"
            )
            test = ["CMD", "node", "-e", js]
        else:
            # Python is assumed present in most base images for python runtime.
            py = f"import urllib.request; urllib.request.urlopen('{url}', timeout=2).read()"
            test = ["CMD", "python", "-c", py]

        return {
            "test": test,
            "interval": "10s",
            "timeout": "3s",
            "retries": 12,
            "start_period": "20s",
        }

    # Fallback: TCP check on the port (no need for curl/wget)
    if runtime == "node":
        js = (
            "const net=require('net');"
            f"const s=net.createConnection({port},'127.0.0.1');"
            "s.on('connect',()=>{s.end();process.exit(0)});"
            "s.on('error',()=>process.exit(1));"
        )
        test = ["CMD", "node", "-e", js]
    else:
        py = (
            "import socket; s=socket.socket(); s.settimeout(2); "
            f"s.connect(('127.0.0.1',{port})); s.close()"
        )
        test = ["CMD", "python", "-c", py]

    return {
        "test": test,
        "interval": "10s",
        "timeout": "3s",
        "retries": 12,
        "start_period": "20s",
    }


def _render_dockerfile(plan: DockerfilePlan) -> str:
    # Minimal best-effort Dockerfile per runtime.
    if plan.runtime == "node":
        cmd = plan.start_cmd or ["npm", "start"]
        return "\n".join(
            [
                "FROM node:20-alpine",
                "WORKDIR /app",
                "COPY package*.json ./",
                "RUN npm ci --omit=dev || npm install",
                "COPY . .",
                f"EXPOSE {plan.port}",
                "ENV NODE_ENV=production",
                f"CMD {_render_json_array(cmd)}",
                "",
            ]
        )

    if plan.runtime == "python":
        env_lines = []
        if plan.extra_env:
            for k, v in plan.extra_env.items():
                env_lines.append(f"ENV {k}={_shell_escape_env(v)}")

        cmd = plan.start_cmd
        return "\n".join(
            [
                "FROM python:3.12-slim",
                "WORKDIR /app",
                "ENV PYTHONDONTWRITEBYTECODE=1",
                "ENV PYTHONUNBUFFERED=1",
                "COPY . .",
                "RUN if [ -f requirements.txt ]; then pip install --no-cache-dir -r requirements.txt; \\",
                "    elif [ -f pyproject.toml ]; then pip install --no-cache-dir .; \\",
                "    else true; fi",
                *env_lines,
                f"EXPOSE {plan.port}",
                (
                    f"CMD {_render_json_array(cmd)}"
                    if cmd is not None
                    else f"CMD {_render_json_array(['python','-m','http.server',str(plan.port)])}"
                ),
                "",
            ]
        )

    if plan.runtime == "java":
        java_version = plan.java_version or 17

        # Prefer a build stage so "docker compose build" works from a repo URL clone.
        if plan.java_build_tool == "maven":
            return "\n".join(
                [
                    f"ARG JAVA_VERSION={java_version}",
                    "FROM maven:3.9-eclipse-temurin-${JAVA_VERSION} AS build",
                    "WORKDIR /src",
                    "COPY . .",
                    "RUN mvn -DskipTests package",
                    "RUN set -eux; \\",
                    "    mkdir -p /out; \\",
                    "    JAR=\"$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*.jar.original' | head -n 1)\"; \\",
                    "    test -n \"$JAR\"; \\",
                    "    cp \"$JAR\" /out/app.jar",
                    "",
                    "FROM eclipse-temurin:${JAVA_VERSION}-jre",
                    "WORKDIR /app",
                    "COPY --from=build /out/app.jar /app/app.jar",
                    f"EXPOSE {plan.port}",
                    "CMD [\"java\", \"-jar\", \"/app/app.jar\"]",
                    "",
                ]
            )

        if plan.java_build_tool == "gradle":
            # Wrapper-first build. Many Gradle projects include gradlew; if not, the build may need manual adjustment.
            return "\n".join(
                [
                    f"ARG JAVA_VERSION={java_version}",
                    "FROM eclipse-temurin:${JAVA_VERSION}-jdk AS build",
                    "WORKDIR /src",
                    "COPY . .",
                    "RUN chmod +x gradlew || true",
                    "RUN if [ -f ./gradlew ]; then ./gradlew build -x test; else echo 'Missing gradlew; add Gradle wrapper or adjust Dockerfile.'; exit 1; fi",
                    "RUN set -eux; \\",
                    "    mkdir -p /out; \\",
                    "    JAR=\"$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)\"; \\",
                    "    test -n \"$JAR\"; \\",
                    "    cp \"$JAR\" /out/app.jar",
                    "",
                    "FROM eclipse-temurin:${JAVA_VERSION}-jre",
                    "WORKDIR /app",
                    "COPY --from=build /out/app.jar /app/app.jar",
                    f"EXPOSE {plan.port}",
                    "CMD [\"java\", \"-jar\", \"/app/app.jar\"]",
                    "",
                ]
            )

        # Fallback: keep it minimal, but still try Maven first since it's the most common.
        return "\n".join(
            [
                f"ARG JAVA_VERSION={java_version}",
                "FROM maven:3.9-eclipse-temurin-${JAVA_VERSION} AS build",
                "WORKDIR /src",
                "COPY . .",
                "RUN mvn -DskipTests package",
                "RUN set -eux; mkdir -p /out; JAR=\"$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*.jar.original' | head -n 1)\"; test -n \"$JAR\"; cp \"$JAR\" /out/app.jar",
                "",
                "FROM eclipse-temurin:${JAVA_VERSION}-jre",
                "WORKDIR /app",
                "COPY --from=build /out/app.jar /app/app.jar",
                f"EXPOSE {plan.port}",
                "CMD [\"java\", \"-jar\", \"/app/app.jar\"]",
                "",
            ]
        )

    if plan.runtime == "dotnet":
        return "\n".join(
            [
                "FROM mcr.microsoft.com/dotnet/aspnet:8.0",
                "WORKDIR /app",
                "# TODO: publish your app and copy published output here",
                "COPY . .",
                f"EXPOSE {plan.port}",
                "# TODO: replace with your dll name",
                "CMD [\"dotnet\", \"YourApp.dll\"]",
                "",
            ]
        )

    if plan.runtime == "go":
        return "\n".join(
            [
                "FROM golang:1.22-alpine as build",
                "WORKDIR /src",
                "COPY . .",
                "RUN go build -o /out/app ./...",
                "",
                "FROM alpine:3.20",
                "WORKDIR /app",
                "COPY --from=build /out/app /app/app",
                f"EXPOSE {plan.port}",
                "CMD [\"/app/app\"]",
                "",
            ]
        )

    return "\n".join(
        [
            "# TODO: No Dockerfile template for this runtime",
            "FROM alpine:3.20",
            "WORKDIR /app",
            "COPY . .",
            "CMD [\"sh\", \"-lc\", \"echo TODO\"]",
            "",
        ]
    )


def _render_json_array(items: list[str]) -> str:
    import json

    return json.dumps(items)


def _shell_escape_env(value: str) -> str:
    # Keep it simple: quote if it contains spaces or special chars.
    if re_needs_quotes(value):
        escaped = value.replace("\\", "\\\\").replace('"', '\\"')
        return f'"{escaped}"'
    return value


def re_needs_quotes(value: str) -> bool:
    import re

    return bool(re.search(r"[^a-zA-Z0-9_./:-]", value))


def _collect_required_env_vars(compose: dict[str, Any]) -> list[str]:
    """Find variables referenced as required placeholders like ${VAR:?set VAR}."""

    import re

    found: set[str] = set()
    pattern = re.compile(r"\$\{([A-Z0-9_]+):\?[^}]*\}")

    def walk(v: Any) -> None:
        if isinstance(v, dict):
            for vv in v.values():
                walk(vv)
        elif isinstance(v, list):
            for vv in v:
                walk(vv)
        elif isinstance(v, str):
            for m in pattern.finditer(v):
                found.add(m.group(1))

    walk(compose)
    return sorted(found)


def _render_env_example(vars_list: list[str]) -> str:
    lines = [
        "# Copy to .env and fill values before running: docker compose up",
        "# Required variables were detected from ${VAR:?} placeholders.",
        "",
    ]
    for v in vars_list:
        lines.append(f"{v}=")
    lines.append("")
    return "\n".join(lines)


def _relpath(base: pathlib.Path, target: pathlib.Path) -> str:
    import os

    try:
        rel = os.path.relpath(str(target), start=str(base))
        rel = rel.replace("\\", "/")
        if rel == ".":
            return "."
        if not rel.startswith("./") and not rel.startswith("../"):
            rel = "./" + rel
        return rel
    except Exception:
        # Different drive or other issue: fall back to absolute
        return str(target)
