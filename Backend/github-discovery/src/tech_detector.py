"""Technology stack detection for repositories"""

import json
from pathlib import Path
from typing import Dict, List, Set, Tuple
from dataclasses import dataclass, field

from .utils.logger import get_logger
from .utils.errors import TechStackDetectionError


@dataclass
class TechStack:
    """Detected technology stack information"""
    languages: Set[str] = field(default_factory=set)
    frameworks: Dict[str, str] = field(default_factory=dict)  # language -> framework
    package_managers: Set[str] = field(default_factory=set)
    has_graphql: bool = False
    has_rest: bool = False
    details: Dict[str, any] = field(default_factory=dict)


class TechStackDetector:
    """Detects technology stack from repository files"""

    # Language detection by file presence
    LANGUAGE_INDICATORS = {
        "nodejs": {
            "files": ["package.json", "yarn.lock", "package-lock.json", "pnpm-lock.yaml"],
            "frameworks": {
                "express": ["express", "expressjs"],
                "fastify": ["fastify"],
                "koa": ["koa"],
                "nestjs": ["@nestjs"],
                "hapi": ["@hapi"],
            }
        },
        "python": {
            "files": ["requirements.txt", "pyproject.toml", "setup.py", "Pipfile", "poetry.lock"],
            "frameworks": {
                "flask": ["flask"],
                "django": ["django"],
                "fastapi": ["fastapi"],
                "bottle": ["bottle"],
                "tornado": ["tornado"],
            }
        },
        "java": {
            "files": ["pom.xml", "build.gradle", "build.gradle.kts", ".gradle"],
            "frameworks": {
                "spring": ["org.springframework", "spring-boot", "spring-web"],
                "jaxrs": ["javax.ws.rs", "jakarta.ws.rs"],
            }
        },
        "csharp": {
            "files": [".csproj", ".sln", ".vbproj", "packages.config"],
            "frameworks": {
                "aspnetcore": ["Microsoft.AspNetCore", "Microsoft.NETCore.App"],
                "webapi": ["System.Web.Http"],
            }
        },
        "go": {
            "files": ["go.mod", "go.sum", "Gopkg.toml"],
            "frameworks": {
                "gin": ["github.com/gin-gonic/gin"],
                "echo": ["github.com/labstack/echo"],
                "mux": ["github.com/gorilla/mux"],
                "fiber": ["github.com/gofiber/fiber"],
            }
        },
        "ruby": {
            "files": ["Gemfile", "Gemfile.lock", "Rakefile"],
            "frameworks": {
                "rails": ["rails"],
                "sinatra": ["sinatra"],
            }
        },
        "php": {
            "files": ["composer.json", "composer.lock"],
            "frameworks": {
                "laravel": ["laravel/framework"],
                "symfony": ["symfony"],
                "lumen": ["lumen"],
            }
        }
    }

    # GraphQL detection patterns
    GRAPHQL_PATTERNS = [
        "graphql",
        "GraphQL",
        "gql",
        "Apollo",
        "Relay",
    ]

    def __init__(self, repo_path: Path):
        self.repo_path = repo_path
        self.logger = get_logger(__name__)

    def detect(self) -> TechStack:
        """
        Detect technology stack in repository.

        Returns:
            TechStack object with detected languages, frameworks, etc.

        Raises:
            TechStackDetectionError: If detection fails
        """
        try:
            stack = TechStack()

            # Detect by file presence
            self._detect_by_files(stack)

            # Detect by package manager files content
            self._detect_from_package_files(stack)

            # Detect GraphQL
            self._detect_graphql(stack)

            # Detect REST frameworks
            self._detect_rest_frameworks(stack)

            self.logger.info(
                "Tech stack detected",
                languages=list(stack.languages),
                frameworks=stack.frameworks,
                has_graphql=stack.has_graphql,
                has_rest=stack.has_rest
            )

            return stack

        except Exception as e:
            raise TechStackDetectionError(f"Failed to detect tech stack: {str(e)}") from e

    def _detect_by_files(self, stack: TechStack) -> None:
        """Detect languages by presence of indicator files"""
        for language, config in self.LANGUAGE_INDICATORS.items():
            for indicator_file in config["files"]:
                if (self.repo_path / indicator_file).exists():
                    stack.languages.add(language)
                    stack.package_managers.add(self._get_package_manager(language))
                    self.logger.debug("Detected language", language=language, file=indicator_file)
                    break

    def _detect_from_package_files(self, stack: TechStack) -> None:
        """Parse package files to detect frameworks"""
        # Node.js: package.json
        package_json = self.repo_path / "package.json"
        if package_json.exists():
            try:
                with open(package_json, "r", encoding="utf-8") as f:
                    data = json.load(f)

                dependencies = {**data.get("dependencies", {}), **data.get("devDependencies", {})}

                for framework, keywords in self.LANGUAGE_INDICATORS["nodejs"]["frameworks"].items():
                    if any(any(kw in dep for kw in keywords) for dep in dependencies.keys()):
                        stack.frameworks["nodejs"] = framework
                        self.logger.debug("Detected Node.js framework", framework=framework)
                        break
            except Exception as e:
                self.logger.warning("Failed to parse package.json", error=str(e))

        # Python: requirements.txt or pyproject.toml
        requirements_txt = self.repo_path / "requirements.txt"
        if requirements_txt.exists():
            try:
                content = requirements_txt.read_text(encoding="utf-8")
                for framework, keywords in self.LANGUAGE_INDICATORS["python"]["frameworks"].items():
                    if any(keyword in content.lower() for keyword in keywords):
                        stack.frameworks["python"] = framework
                        self.logger.debug("Detected Python framework", framework=framework)
                        break
            except Exception as e:
                self.logger.warning("Failed to parse requirements.txt", error=str(e))

        # Java: pom.xml
        pom_xml = self.repo_path / "pom.xml"
        if pom_xml.exists():
            try:
                content = pom_xml.read_text(encoding="utf-8")
                for framework, keywords in self.LANGUAGE_INDICATORS["java"]["frameworks"].items():
                    if any(keyword in content for keyword in keywords):
                        stack.frameworks["java"] = framework
                        self.logger.debug("Detected Java framework", framework=framework)
                        break
            except Exception as e:
                self.logger.warning("Failed to parse pom.xml", error=str(e))

        # C#: .csproj
        csproj_files = list(self.repo_path.rglob("*.csproj"))
        if csproj_files:
            try:
                content = csproj_files[0].read_text(encoding="utf-8")
                for framework, keywords in self.LANGUAGE_INDICATORS["csharp"]["frameworks"].items():
                    if any(keyword in content for keyword in keywords):
                        stack.frameworks["csharp"] = framework
                        self.logger.debug("Detected C# framework", framework=framework)
                        break
            except Exception as e:
                self.logger.warning("Failed to parse .csproj", error=str(e))

    def _detect_graphql(self, stack: TechStack) -> None:
        """Detect GraphQL usage"""
        for pattern in self.GRAPHQL_PATTERNS:
            matches = list(self.repo_path.rglob(f"*{pattern}*"))
            if matches:
                stack.has_graphql = True
                self.logger.debug("Detected GraphQL usage", pattern=pattern)
                break

    def _detect_rest_frameworks(self, stack: TechStack) -> None:
        """Detect REST API frameworks"""
        # If we detected a framework, assume REST
        if stack.frameworks:
            stack.has_rest = True

    def _get_package_manager(self, language: str) -> str:
        """Get package manager for language"""
        mapping = {
            "nodejs": "npm",
            "python": "pip",
            "java": "maven",
            "csharp": "nuget",
            "go": "go",
            "ruby": "bundler",
            "php": "composer",
        }
        return mapping.get(language, "unknown")