"""Technology stack detection for repositories"""

import json
from pathlib import Path
from typing import Dict, List, Set, Tuple
from dataclasses import dataclass, field

from .utils.logger import get_logger
from .utils.errors import TechStackDetectionError
from .registry import detect_languages_by_files, detect_frameworks_from_repo


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
        languages, package_managers = detect_languages_by_files(self.repo_path)
        stack.languages.update(languages)
        stack.package_managers.update(package_managers)

    def _detect_from_package_files(self, stack: TechStack) -> None:
        """Parse package files to detect frameworks"""
        try:
            detected = detect_frameworks_from_repo(self.repo_path)
            stack.frameworks.update(detected)
        except Exception as e:
            self.logger.warning("Framework detection failed", error=str(e))

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