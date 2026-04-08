from __future__ import annotations

import json
import pathlib
import re
import dataclasses
from typing import Optional


def detect_project_kind(project_dir: pathlib.Path) -> str:
    # coarse detection (multi-stack projects will be treated by priority)
    if (project_dir / "package.json").exists():
        return "node"
    if (project_dir / "pyproject.toml").exists() or (project_dir / "requirements.txt").exists():
        return "python"
    if (project_dir / "pom.xml").exists() or any(project_dir.glob("**/pom.xml")):
        return "java-maven"
    if (project_dir / "build.gradle").exists() or (project_dir / "build.gradle.kts").exists():
        return "java-gradle"
    if any(project_dir.glob("**/*.csproj")):
        return "dotnet"
    if (project_dir / "go.mod").exists():
        return "go"
    if (project_dir / "Gemfile").exists():
        return "ruby"
    if (project_dir / "composer.json").exists():
        return "php"
    return "unknown"


def find_service_roots(project_dir: pathlib.Path) -> list[pathlib.Path]:
    """Detect multiple service roots in a repo (monorepo).

    A service root is a folder that contains a clear project marker like:
    - package.json
    - pyproject.toml / requirements.txt
    - go.mod
    - *.csproj
    - pom.xml / build.gradle

    If none found, fallback to the repo root as a single service.
    """

    markers = {
        "package.json",
        "pyproject.toml",
        "requirements.txt",
        "go.mod",
        "pom.xml",
        "build.gradle",
        "build.gradle.kts",
        "Gemfile",
        "composer.json",
    }

    roots: set[pathlib.Path] = set()
    # include root if it is a project itself
    if any((project_dir / m).exists() for m in markers) or any(project_dir.glob("*.csproj")):
        roots.add(project_dir)

    # scan subfolders but keep it bounded
    for p in project_dir.glob("**/*"):
        if not p.is_dir():
            continue
        rel = p.relative_to(project_dir)
        if len(rel.parts) > 4:
            continue
        if any(part.startswith(".") for part in rel.parts):
            continue
        if any(part in {"node_modules", "dist", "build", "target", "bin", "obj", ".git"} for part in rel.parts):
            continue

        if any((p / m).exists() for m in markers) or any(p.glob("*.csproj")):
            roots.add(p)

    # prefer deeper roots if root is just an umbrella
    roots_list = sorted(roots, key=lambda x: (len(x.relative_to(project_dir).parts), str(x)))
    if not roots_list:
        return [project_dir]

    # Remove roots that are parents of other roots, unless it's the only one.
    final: list[pathlib.Path] = []
    for r in roots_list:
        if any(other != r and other.is_relative_to(r) for other in roots_list):
            # umbrella folder
            continue
        final.append(r)

    return final or [project_dir]


def detect_runtime_and_port(project_dir: pathlib.Path, project_kind: str) -> tuple[str, int]:
    # runtime is used for Dockerfile and healthcheck command
    if project_kind == "node":
        port = _guess_node_port(project_dir) or _guess_port_from_env_files(project_dir) or 3000
        return "node", port
    if project_kind == "python":
        framework = _guess_python_framework(project_dir)
        port = _guess_port_from_env_files(project_dir)
        if port is not None:
            return "python", port
        if framework in {"fastapi", "django"}:
            return "python", 8000
        if framework == "flask":
            return "python", 5000
        return "python", 8000
    if project_kind.startswith("java"):
        return "java", _guess_java_port(project_dir) or 8080
    if project_kind == "dotnet":
        return "dotnet", _guess_dotnet_port(project_dir) or 8080
    if project_kind == "go":
        return "go", _guess_port_from_env_files(project_dir) or 8080
    if project_kind == "ruby":
        return "ruby", 3000
    if project_kind == "php":
        return "php", 8080
    return "unknown", 8080


def detect_node_start(project_dir: pathlib.Path) -> Optional[list[str]]:
    pkg = project_dir / "package.json"
    if pkg.exists():
        try:
            data = json.loads(pkg.read_text(encoding="utf-8"))
            scripts = data.get("scripts") or {}
            if "start" in scripts:
                return ["npm", "start"]
        except Exception:
            pass

    for candidate in ["server.js", "index.js", "app.js"]:
        if (project_dir / candidate).exists():
            return ["node", candidate]

    return None


@dataclasses.dataclass(frozen=True)
class PythonStart:
    start_cmd: Optional[list[str]]
    extra_env: Optional[dict[str, str]]


def detect_python_start(project_dir: pathlib.Path, *, port: int) -> PythonStart:
    framework = _guess_python_framework(project_dir)

    if framework == "django":
        if (project_dir / "manage.py").exists() or any(project_dir.glob("**/manage.py")):
            return PythonStart(
                start_cmd=["python", "manage.py", "runserver", f"0.0.0.0:{port}"],
                extra_env=None,
            )

    if framework == "flask":
        flask_app = _guess_flask_app_file(project_dir)
        if flask_app is not None:
            return PythonStart(
                start_cmd=["python", "-m", "flask", "run", "--host", "0.0.0.0", "--port", str(port)],
                extra_env={"FLASK_APP": flask_app},
            )
        # If we can't infer FLASK_APP reliably, don't guess.
        return PythonStart(start_cmd=None, extra_env=None)

    if framework == "fastapi":
        app_ref = _find_fastapi_app_ref(project_dir)
        if app_ref is not None:
            return PythonStart(
                start_cmd=[
                    "python",
                    "-m",
                    "uvicorn",
                    app_ref,
                    "--host",
                    "0.0.0.0",
                    "--port",
                    str(port),
                ],
                extra_env=None,
            )
        return PythonStart(start_cmd=None, extra_env=None)

    return PythonStart(start_cmd=None, extra_env=None)


def detect_db(project_dir: pathlib.Path, project_kind: str) -> Optional[str]:
    # If a compose already exists, we won't attempt to read it here (avoid complexity).
    # Detect by dependencies / keywords.
    text_blobs: list[str] = []

    pkg = project_dir / "package.json"
    if pkg.exists():
        try:
            data = json.loads(pkg.read_text(encoding="utf-8"))
            deps = {}
            deps.update(data.get("dependencies") or {})
            deps.update(data.get("devDependencies") or {})
            text_blobs.append(" ".join(deps.keys()))
        except Exception:
            pass

    req = project_dir / "requirements.txt"
    if req.exists():
        try:
            text_blobs.append(req.read_text(encoding="utf-8", errors="ignore"))
        except Exception:
            pass

    pyproject = project_dir / "pyproject.toml"
    if pyproject.exists():
        try:
            # heuristic only: look for package names in file text
            text_blobs.append(pyproject.read_text(encoding="utf-8", errors="ignore"))
        except Exception:
            pass

    composer = project_dir / "composer.json"
    if composer.exists():
        try:
            text_blobs.append(composer.read_text(encoding="utf-8", errors="ignore"))
        except Exception:
            pass

    # broad scan of config/env files
    for p in project_dir.glob("**/.env*"):
        if p.is_file() and p.stat().st_size < 200_000:
            try:
                text_blobs.append(p.read_text(encoding="utf-8", errors="ignore"))
            except Exception:
                pass

    haystack = "\n".join(text_blobs).lower()

    if re.search(r"\b(redis|ioredis)\b", haystack):
        return "redis"
    if re.search(r"\b(mongoose|mongodb|pymongo|mongoengine)\b", haystack):
        return "mongo"
    if re.search(r"\b(mysql2|mysqlclient|pymysql|mariadb)\b", haystack):
        return "mysql"
    if re.search(r"\b(pg|postgres|psycopg2|psycopg|asyncpg)\b", haystack):
        return "postgres"

    # if explicit DB env vars exist
    if "database_url" in haystack or "db_host" in haystack:
        # ambiguous; do not guess which DB
        return None

    return None


def detect_health_path(project_dir: pathlib.Path) -> Optional[str]:
    # Try to find a conventional health endpoint in source.
    # Only lightweight scanning (avoid huge repos).
    candidates = ["/health", "/healthz", "/status", "/_health"]

    # If there's a docs mention, prefer it.
    for doc in [project_dir / "README.md", project_dir / "readme.md", project_dir / "docs" / "README.md"]:
        if doc.exists() and doc.is_file() and doc.stat().st_size < 500_000:
            text = doc.read_text(encoding="utf-8", errors="ignore").lower()
            for c in candidates:
                if c in text:
                    return c

    # Grep-like scan of a few source extensions
    exts = {".py", ".js", ".ts", ".java", ".cs", ".go", ".rb", ".php"}
    for p in project_dir.glob("**/*"):
        if not p.is_file():
            continue
        if p.suffix.lower() not in exts:
            continue
        if p.stat().st_size > 300_000:
            continue
        try:
            text = p.read_text(encoding="utf-8", errors="ignore").lower()
        except Exception:
            continue
        if "health" in text or "healthz" in text:
            for c in candidates:
                if c.strip("/") in text:
                    return c

    return None


def detect_java_build_tool(project_dir: pathlib.Path) -> Optional[str]:
    """Detect Java build tool for a service root.

    Returns 'maven', 'gradle', or None.
    """

    if (project_dir / "pom.xml").exists() or (project_dir / "mvnw").exists():
        return "maven"
    if (project_dir / "build.gradle").exists() or (project_dir / "build.gradle.kts").exists() or (project_dir / "gradlew").exists():
        return "gradle"
    return None


def detect_java_version(project_dir: pathlib.Path) -> Optional[int]:
    """Best-effort Java major version detection.

    We avoid guessing. If not found, returns None.
    """

    # Maven: look for common properties in pom.xml
    pom = project_dir / "pom.xml"
    if pom.exists() and pom.is_file() and pom.stat().st_size < 2_000_000:
        try:
            text = pom.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            text = ""

        for pat in [
            r"<java\.version>\s*(\d{1,2})\s*</java\.version>",
            r"<maven\.compiler\.release>\s*(\d{1,2})\s*</maven\.compiler\.release>",
            r"<maven\.compiler\.target>\s*(\d{1,2})\s*</maven\.compiler\.target>",
            r"<maven\.compiler\.source>\s*(\d{1,2})\s*</maven\.compiler\.source>",
        ]:
            m = re.search(pat, text)
            if m:
                try:
                    v = int(m.group(1))
                    if 8 <= v <= 25:
                        return v
                except Exception:
                    continue

    # Gradle: inspect build.gradle / build.gradle.kts
    for gradle_file in [project_dir / "build.gradle", project_dir / "build.gradle.kts"]:
        if not gradle_file.exists() or not gradle_file.is_file() or gradle_file.stat().st_size > 2_000_000:
            continue
        try:
            text = gradle_file.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue

        patterns = [
            # toolchain { languageVersion = JavaLanguageVersion.of(17) }
            r"JavaLanguageVersion\.of\((\d{1,2})\)",
            # sourceCompatibility = JavaVersion.VERSION_17
            r"sourceCompatibility\s*=\s*JavaVersion\.VERSION_(\d{1,2})",
            r"targetCompatibility\s*=\s*JavaVersion\.VERSION_(\d{1,2})",
            # sourceCompatibility = '17'
            r"sourceCompatibility\s*=\s*['\"](\d{1,2})['\"]",
            r"targetCompatibility\s*=\s*['\"](\d{1,2})['\"]",
            # Kotlin DSL / kotlinOptions.jvmTarget = "17"
            r"jvmTarget\s*=\s*['\"](\d{1,2})['\"]",
        ]
        for pat in patterns:
            m = re.search(pat, text)
            if m:
                try:
                    v = int(m.group(1))
                    if 8 <= v <= 25:
                        return v
                except Exception:
                    continue

    return None


def _guess_node_port(project_dir: pathlib.Path) -> Optional[int]:
    pkg = project_dir / "package.json"
    if not pkg.exists():
        return None
    try:
        data = json.loads(pkg.read_text(encoding="utf-8"))
    except Exception:
        return None

    scripts = data.get("scripts") or {}
    joined = "\n".join([str(v) for v in scripts.values()])

    m = re.search(r"\bPORT\s*=\s*(\d{2,5})\b", joined)
    if m:
        return int(m.group(1))

    return None


def _guess_port_from_env_files(project_dir: pathlib.Path) -> Optional[int]:
    import re

    for p in [project_dir / ".env", project_dir / ".env.example", project_dir / ".env.sample"]:
        if p.exists() and p.is_file() and p.stat().st_size < 200_000:
            text = p.read_text(encoding="utf-8", errors="ignore")
            m = re.search(r"^\s*(PORT|APP_PORT|HTTP_PORT)\s*=\s*(\d{2,5})\s*$", text, flags=re.MULTILINE)
            if m:
                return int(m.group(2))
    return None


def _guess_java_port(project_dir: pathlib.Path) -> Optional[int]:
    import re

    resources = project_dir / "src" / "main" / "resources"
    candidates: list[pathlib.Path] = []

    # Common Spring Boot config locations
    for base in [resources, resources / "config", project_dir / "config"]:
        if base.exists() and base.is_dir():
            for pat in [
                "application*.properties",
                "application*.yml",
                "application*.yaml",
                "bootstrap*.properties",
                "bootstrap*.yml",
                "bootstrap*.yaml",
            ]:
                candidates.extend(list(base.glob(pat)))

    # Fallback: bounded scan for any small config mentioning server.port
    if resources.exists() and resources.is_dir():
        for p in resources.rglob("*"):
            if not p.is_file():
                continue
            if p.suffix.lower() not in {".yml", ".yaml", ".properties"}:
                continue
            if p.stat().st_size > 500_000:
                continue
            candidates.append(p)

    # de-dup preserving order
    seen: set[pathlib.Path] = set()
    uniq: list[pathlib.Path] = []
    for p in candidates:
        if p in seen:
            continue
        seen.add(p)
        uniq.append(p)

    for p in uniq:
        try:
            text = p.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue

        if p.suffix.lower() == ".properties":
            port = _parse_spring_port_from_properties(text)
            if port is not None:
                return port

            # sometimes: server.port: 8082 in .properties-like files
            m = re.search(r"^\s*server\.port\s*[:=]\s*(\d{2,5})\s*$", text, flags=re.MULTILINE)
            if m:
                return int(m.group(1))

        elif p.suffix.lower() in {".yml", ".yaml"}:
            port = _parse_spring_port_from_yaml(text)
            if port is not None:
                return port

            # last resort regex (won't catch nested YAML)
            m = re.search(r"server\.port\s*[:=]\s*(\d{2,5})", text)
            if m:
                return int(m.group(1))

    return None


def _parse_spring_port_from_properties(text: str) -> Optional[int]:
    import re

    # server.port=8082
    m = re.search(r"^\s*server\.port\s*=\s*(.+?)\s*$", text, flags=re.MULTILINE)
    if not m:
        return None
    raw = m.group(1).strip().strip('"').strip("'")

    # direct int
    if raw.isdigit():
        return int(raw)

    # ${PORT:8082} or ${SERVER_PORT:8082}
    m2 = re.match(r"\$\{[A-Za-z0-9_]+:(\d{2,5})\}$", raw)
    if m2:
        return int(m2.group(1))

    # ${PORT} -> unknown, do not guess
    if re.match(r"\$\{[A-Za-z0-9_]+\}$", raw):
        return None

    return None


def _parse_spring_port_from_yaml(text: str) -> Optional[int]:
    import yaml

    try:
        data = yaml.safe_load(text)
    except Exception:
        return None
    if not isinstance(data, dict):
        return None

    server = data.get("server")
    if isinstance(server, dict):
        port = server.get("port")
        parsed = _coerce_port_value(port)
        if parsed is not None:
            return parsed

    # sometimes flattened
    port = data.get("server.port")
    return _coerce_port_value(port)


def _coerce_port_value(v: object) -> Optional[int]:
    import re

    if isinstance(v, int) and 1 <= v <= 65535:
        return v
    if isinstance(v, str):
        s = v.strip().strip('"').strip("'")
        if s.isdigit():
            n = int(s)
            return n if 1 <= n <= 65535 else None
        m = re.match(r"\$\{[A-Za-z0-9_]+:(\d{2,5})\}$", s)
        if m:
            n = int(m.group(1))
            return n if 1 <= n <= 65535 else None
        # ${PORT} unknown
        if re.match(r"\$\{[A-Za-z0-9_]+\}$", s):
            return None
    return None


def _guess_dotnet_port(project_dir: pathlib.Path) -> Optional[int]:
    import json
    import re

    # launchSettings.json often includes applicationUrl
    for p in project_dir.glob("**/Properties/launchSettings.json"):
        if not p.is_file() or p.stat().st_size > 500_000:
            continue
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
        except Exception:
            continue
        profiles = (data.get("profiles") or {}).values()
        for prof in profiles:
            url = str((prof or {}).get("applicationUrl", ""))
            m = re.search(r":(\d{2,5})", url)
            if m:
                return int(m.group(1))

    # ASPNETCORE_URLS env in .env
    for p in [project_dir / ".env", project_dir / ".env.example"]:
        if p.exists() and p.is_file() and p.stat().st_size < 200_000:
            txt = p.read_text(encoding="utf-8", errors="ignore")
            m = re.search(r"ASPNETCORE_URLS\s*=\s*https?://\+:(\d{2,5})", txt)
            if m:
                return int(m.group(1))

    return None


def _guess_python_framework(project_dir: pathlib.Path) -> str:
    # heuristic: scan requirements/pyproject for framework names
    needles = {
        "fastapi": "fastapi",
        "django": "django",
        "flask": "flask",
    }

    text = ""
    for p in [project_dir / "requirements.txt", project_dir / "pyproject.toml"]:
        if p.exists():
            try:
                text += "\n" + p.read_text(encoding="utf-8", errors="ignore").lower()
            except Exception:
                pass

    for k, v in needles.items():
        if k in text:
            return v

    # fallback: look for manage.py (Django)
    if (project_dir / "manage.py").exists() or any(project_dir.glob("**/manage.py")):
        return "django"

    return "unknown"


def _guess_flask_app_file(project_dir: pathlib.Path) -> Optional[str]:
    # Common flask entry files
    for name in ["app.py", "wsgi.py", "main.py", "server.py"]:
        p = project_dir / name
        if p.exists() and p.is_file() and p.stat().st_size < 200_000:
            try:
                text = p.read_text(encoding="utf-8", errors="ignore")
            except Exception:
                continue
            if "Flask(" in text or "from flask" in text:
                return name
    return None


def _find_fastapi_app_ref(project_dir: pathlib.Path) -> Optional[str]:
    # Tries to find something like: app = FastAPI(...)
    # Returns <module>:<var> (e.g. main:app)
    candidates = ["main.py", "app.py", "server.py"]
    for name in candidates:
        p = project_dir / name
        if not p.exists() or not p.is_file() or p.stat().st_size > 300_000:
            continue
        try:
            text = p.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        if "FastAPI(" not in text:
            continue
        m = re.search(r"^\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*=\s*FastAPI\s*\(", text, flags=re.MULTILINE)
        if m:
            var = m.group(1)
            module = pathlib.Path(name).stem
            return f"{module}:{var}"
    return None
