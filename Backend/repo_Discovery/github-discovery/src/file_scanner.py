"""File scanner with regex pre-filtering"""

import re
from pathlib import Path
from typing import List, Set, Generator
from dataclasses import dataclass

from .utils.logger import get_logger
from .utils.errors import FileProcessingError


@dataclass
class ScanPatterns:
    """Regex patterns for different frameworks to identify route definitions"""
    # Node.js/Express patterns
    express_route = r"(?:app|router)\.(get|post|put|delete|patch|all)\s*\(\s*['\"`]([^'\"`]+)['\"`]"
    express_middleware = r"(?:app|router)\.use\s*\(\s*['\"`]([^'\"`]+)['\"`]"

    # Python/Flask patterns
    flask_route = r"@(?:app|bp|blueprint)\.route\s*\(\s*['\"`]([^'\"`]+)['\"`]"
    flask_methods = r"methods\s*=\s*\[([^\]]+)\]"

    # FastAPI patterns
    fastapi_route = r"@(?:app|router)\.(get|post|put|delete|patch)\s*\(\s*['\"`]([^'\"`]+)['\"`]"

    # Django patterns
    django_path = r"path\s*\(\s*['\"`]([^'\"`]+)['\"`]"

    # Java/Spring patterns
    spring_mapping = r"@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\s*\(\s*['\"`]([^'\"`]+)['\"`]"

    # C# ASP.NET patterns
    aspnet_route = r"\[(?:HttpGet|HttpPost|HttpPut|HttpDelete|HttpPatch|Route)\s*\(\s*['\"`]([^'\"`]+)['\"`]"

    # GraphQL patterns
    graphql_type = r"type\s+(?:Query|Mutation)\s*\{"
    graphql_resolver = r"@Resolver\s*\("


class FileScanner:
    """Scans repository files for potential API endpoint definitions"""

    # Directories to ignore
    IGNORED_DIRS = {
        "node_modules", "venv", ".venv", "env", ".env", "__pycache__",
        "build", "dist", "target", "bin", "obj", ".git", ".idea", ".vscode",
        "coverage", ".coverage", "docs", "test", "tests", "spec", "specs",
        "migrations", "seeders", "fixtures", "public", "static", "assets"
    }

    # File extensions to process
    TARGET_EXTENSIONS = {
        ".js", ".jsx", ".ts", ".tsx",  # JavaScript/TypeScript
        ".py",  # Python
        ".java",  # Java
        ".cs", ".cshtml",  # C#
        ".go",  # Go
        ".rb",  # Ruby
        ".php",  # PHP
    }

    def __init__(self, repo_path: Path):
        self.repo_path = repo_path
        self.logger = get_logger(__name__)

    def scan_files(self) -> List[Path]:
        """
        Scan repository for files that may contain API endpoints.

        Returns:
            List of file paths to process

        Raises:
            FileProcessingError: If scanning fails
        """
        try:
            self.logger.info("Starting file scan", repo_path=str(self.repo_path))
            candidate_files = []

            for file_path in self.repo_path.rglob("*"):
                # Skip ignored directories
                if any(part in self.IGNORED_DIRS for part in file_path.parts):
                    continue

                # Skip non-files
                if not file_path.is_file():
                    continue

                # Skip hidden files
                if file_path.name.startswith("."):
                    continue

                # Check extension
                if file_path.suffix not in self.TARGET_EXTENSIONS:
                    continue

                # Skip large files (> 1MB)
                try:
                    if file_path.stat().st_size > 1_000_000:
                        self.logger.debug("Skipping large file", file=str(file_path), size_mb=file_path.stat().st_size / 1_000_000)
                        continue
                except OSError:
                    continue

                # Include ALL files matching target extensions
                # (AST parsing is better than regex for detection)
                candidate_files.append(file_path)

            self.logger.info(
                "File scan complete",
                total_files=len(candidate_files),
                candidates=candidate_files
            )
            return candidate_files

        except Exception as e:
            raise FileProcessingError(f"Failed to scan files: {str(e)}") from e

            self.logger.info(
                "File scan complete",
                total_files=len(candidate_files),
                candidates=candidate_files
            )
            return candidate_files

        except Exception as e:
            raise FileProcessingError(f"Failed to scan files: {str(e)}") from e

    def _prefilter_file(self, file_path: Path) -> bool:
        """
        Quick regex check if file likely contains route definitions.

        Args:
            file_path: Path to file

        Returns:
            True if file likely contains routes, False otherwise
        """
        try:
            # Skip large files (they'll be chunked later if needed)
            if file_path.stat().st_size > 1_000_000:  # 1MB
                return True  # Process large files anyway, they'll be chunked

            content = file_path.read_text(encoding="utf-8", errors="ignore")

            # Check for route patterns
            patterns = [
                ScanPatterns.express_route,
                ScanPatterns.express_middleware,
                ScanPatterns.flask_route,
                ScanPatterns.fastapi_route,
                ScanPatterns.spring_mapping,
                ScanPatterns.aspnet_route,
                ScanPatterns.graphql_type,
            ]

            for pattern in patterns:
                if re.search(pattern, content, re.IGNORECASE):
                    return True

            return False

        except Exception as e:
            self.logger.debug("Error prefiltering file", file=str(file_path), error=str(e))
            return False  # Skip files we can't read

    def get_language_from_extension(self, file_path: Path) -> str:
        """Determine language from file extension"""
        ext = file_path.suffix.lower()
        mapping = {
            ".js": "javascript",
            ".jsx": "javascript",
            ".ts": "typescript",
            ".tsx": "typescript",
            ".py": "python",
            ".java": "java",
            ".cs": "csharp",
            ".cshtml": "csharp",
            ".go": "go",
            ".rb": "ruby",
            ".php": "php",
        }
        return mapping.get(ext, "unknown")