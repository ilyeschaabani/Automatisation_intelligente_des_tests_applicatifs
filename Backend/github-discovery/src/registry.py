"""Support registry for languages and frameworks.

This module is the single source of truth for:
- which languages/frameworks we claim to support
- how we detect them (indicator files + dependency keywords)

Extraction strategies are implemented elsewhere (layers/config/code/universal).
The registry enables scalable routing and easy extensibility.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Set, Tuple


LanguageId = str
FrameworkId = str


@dataclass(frozen=True)
class LanguageDefinition:
    id: LanguageId
    indicator_files: Tuple[str, ...]
    package_manager: str


@dataclass(frozen=True)
class FrameworkDefinition:
    id: FrameworkId
    language: LanguageId
    dependency_keywords: Tuple[str, ...] = field(default_factory=tuple)


LANGUAGES: Tuple[LanguageDefinition, ...] = (
    LanguageDefinition(
        id="nodejs",
        indicator_files=("package.json", "yarn.lock", "package-lock.json", "pnpm-lock.yaml"),
        package_manager="npm",
    ),
    LanguageDefinition(
        id="python",
        indicator_files=("requirements.txt", "pyproject.toml", "setup.py", "Pipfile", "poetry.lock"),
        package_manager="pip",
    ),
    LanguageDefinition(
        id="java",
        indicator_files=("pom.xml", "build.gradle", "build.gradle.kts", ".gradle"),
        package_manager="maven",
    ),
    LanguageDefinition(
        id="php",
        indicator_files=("composer.json", "composer.lock"),
        package_manager="composer",
    ),
    LanguageDefinition(
        id="go",
        indicator_files=("go.mod", "go.sum", "Gopkg.toml"),
        package_manager="go",
    ),
    LanguageDefinition(
        id="csharp",
        indicator_files=(".sln", "packages.config"),
        package_manager="nuget",
    ),
    LanguageDefinition(
        id="ruby",
        indicator_files=("Gemfile", "Gemfile.lock", "Rakefile"),
        package_manager="bundler",
    ),
)


# Initial support list requested by the user (plus already-supported items).
FRAMEWORKS: Tuple[FrameworkDefinition, ...] = (
    # Java
    FrameworkDefinition(id="spring", language="java", dependency_keywords=("org.springframework", "spring-boot", "spring-web")),
    FrameworkDefinition(id="micronaut", language="java", dependency_keywords=("io.micronaut", "micronaut")),
    FrameworkDefinition(id="quarkus", language="java", dependency_keywords=("io.quarkus", "quarkus")),

    # JavaScript/TypeScript (Node.js)
    FrameworkDefinition(id="express", language="nodejs", dependency_keywords=("express", "expressjs")),
    FrameworkDefinition(id="nestjs", language="nodejs", dependency_keywords=("@nestjs",)),
    FrameworkDefinition(id="fastify", language="nodejs", dependency_keywords=("fastify",)),

    # Python
    FrameworkDefinition(id="fastapi", language="python", dependency_keywords=("fastapi",)),
    FrameworkDefinition(id="flask", language="python", dependency_keywords=("flask",)),
    FrameworkDefinition(id="django", language="python", dependency_keywords=("django",)),
    FrameworkDefinition(id="djangorestframework", language="python", dependency_keywords=("djangorestframework", "django-rest-framework", "rest_framework")),

    # PHP
    FrameworkDefinition(id="symfony", language="php", dependency_keywords=("symfony",)),
    FrameworkDefinition(id="laravel", language="php", dependency_keywords=("laravel/framework", "laravel")),

    # Go
    FrameworkDefinition(id="gin", language="go", dependency_keywords=("github.com/gin-gonic/gin",)),
    FrameworkDefinition(id="echo", language="go", dependency_keywords=("github.com/labstack/echo",)),

    # C#
    FrameworkDefinition(id="aspnetcore", language="csharp", dependency_keywords=("Microsoft.AspNetCore", "Microsoft.AspNetCore.Mvc", "Microsoft.NETCore.App")),
)


def detect_languages_by_files(repo_path: Path) -> Tuple[Set[LanguageId], Set[str]]:
    languages: Set[LanguageId] = set()
    package_managers: Set[str] = set()

    for lang in LANGUAGES:
        for indicator in lang.indicator_files:
            if indicator.startswith("."):
                # Special case: directory indicators like ".gradle"
                if (repo_path / indicator).exists():
                    languages.add(lang.id)
                    package_managers.add(lang.package_manager)
                    break
            else:
                if (repo_path / indicator).exists():
                    languages.add(lang.id)
                    package_managers.add(lang.package_manager)
                    break

    # C# often detected via csproj anywhere
    if list(repo_path.rglob("*.csproj")):
        languages.add("csharp")
        package_managers.add("nuget")

    return languages, package_managers


def detect_frameworks_from_repo(repo_path: Path) -> Dict[LanguageId, FrameworkId]:
    """Best-effort framework detection from common dependency files."""
    detected: Dict[LanguageId, FrameworkId] = {}

    # Node: package.json
    package_json = repo_path / "package.json"
    if package_json.exists():
        try:
            data = json.loads(package_json.read_text(encoding="utf-8", errors="ignore") or "{}")
            deps = {**(data.get("dependencies") or {}), **(data.get("devDependencies") or {})}
            dep_names = [str(k) for k in deps.keys()]
            detected.update(_match_frameworks("nodejs", dep_names))
        except Exception:
            pass

    # Python: requirements.txt
    requirements_txt = repo_path / "requirements.txt"
    if requirements_txt.exists():
        content = requirements_txt.read_text(encoding="utf-8", errors="ignore").lower()
        detected.update(_match_frameworks("python", [content]))

    # Java: pom.xml
    pom_xml = repo_path / "pom.xml"
    if pom_xml.exists():
        content = pom_xml.read_text(encoding="utf-8", errors="ignore")
        detected.update(_match_frameworks("java", [content]))

    # PHP: composer.json
    composer_json = repo_path / "composer.json"
    if composer_json.exists():
        try:
            data = json.loads(composer_json.read_text(encoding="utf-8", errors="ignore") or "{}")
            deps = {**(data.get("require") or {}), **(data.get("require-dev") or {})}
            dep_names = [str(k) for k in deps.keys()]
            detected.update(_match_frameworks("php", dep_names))
        except Exception:
            pass

    # Go: go.mod
    go_mod = repo_path / "go.mod"
    if go_mod.exists():
        content = go_mod.read_text(encoding="utf-8", errors="ignore")
        detected.update(_match_frameworks("go", [content]))

    # C#: first csproj
    csproj_files = list(repo_path.rglob("*.csproj"))
    if csproj_files:
        content = csproj_files[0].read_text(encoding="utf-8", errors="ignore")
        detected.update(_match_frameworks("csharp", [content]))

    return detected


def _match_frameworks(language: LanguageId, haystacks: Iterable[str]) -> Dict[LanguageId, FrameworkId]:
    for fw in FRAMEWORKS:
        if fw.language != language:
            continue
        for hs in haystacks:
            hs_low = hs.lower()
            if any(k.lower() in hs_low for k in fw.dependency_keywords):
                return {language: fw.id}
    return {}
