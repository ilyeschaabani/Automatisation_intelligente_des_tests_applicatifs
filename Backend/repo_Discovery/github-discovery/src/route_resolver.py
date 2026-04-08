"""Route prefix resolver for nested routers and imports"""

from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple
import json
import re

from .utils.logger import get_logger
from .utils.errors import RouteResolutionError
from .file_scanner import FileScanner


class RouteResolver:
    """Resolves route prefixes across files and imports"""

    def __init__(self, repo_path: Path, file_scanner: FileScanner):
        self.repo_path = repo_path
        self.file_scanner = file_scanner
        self.logger = get_logger(__name__)
        self.prefix_cache: Dict[str, str] = {}
        self.import_graph: Dict[Path, List[Path]] = {}

    def resolve_prefixes(self, file_paths: List[Path]) -> Dict[str, str]:
        """
        Resolve router prefixes for all files.

        Args:
            file_paths: List of file paths to process

        Returns:
            Dictionary mapping file paths to their effective router prefixes
        """
        self.logger.info("Resolving route prefixes", file_count=len(file_paths))

        # Build import graph first
        self._build_import_graph(file_paths)

        # Analyze each file for router.use() calls and exports
        prefix_mapping = {}

        for file_path in file_paths:
            try:
                prefix = self._analyze_file_for_prefix(file_path)
                if prefix:
                    prefix_mapping[str(file_path)] = prefix
            except Exception as e:
                self.logger.warning("Failed to analyze file for prefix", file=str(file_path), error=str(e))

        self.logger.info("Prefix resolution complete", prefixes_found=len(prefix_mapping))
        return prefix_mapping

    def _build_import_graph(self, file_paths: List[Path]) -> None:
        """Build graph of file imports to trace router relationships"""
        self.import_graph = {}

        for file_path in file_paths:
            imports = self._extract_imports(file_path)
            self.import_graph[file_path] = imports

    def _extract_imports(self, file_path: Path) -> List[Path]:
        """Extract imported file paths from a source file"""
        imports = []
        try:
            content = file_path.read_text(encoding="utf-8", errors="ignore")
            language = self.file_scanner.get_language_from_extension(file_path)

            if language in ["javascript", "typescript"]:
                imports = self._extract_js_imports(content, file_path)
            elif language == "python":
                imports = self._extract_python_imports(content, file_path)
            elif language == "java":
                imports = self._extract_java_imports(content, file_path)
            elif language == "csharp":
                imports = self._extract_csharp_imports(content, file_path)
            elif language == "go":
                imports = self._extract_go_imports(content, file_path)

        except Exception as e:
            self.logger.debug("Error extracting imports", file=str(file_path), error=str(e))

        return imports

    def _extract_js_imports(self, content: str, base_path: Path) -> List[Path]:
        """Extract ES6 imports from JavaScript/TypeScript"""
        imports = []
        import re

        # Match: import X from 'path' or import 'path' or require('path')
        patterns = [
            r"import\s+.*from\s+['\"]([^'\"]+)['\"]",
            r"import\s+['\"]([^'\"]+)['\"]",
            r"require\s*\(\s*['\"]([^'\"]+)['\"]\s*\)"
        ]

        for pattern in patterns:
            for match in re.finditer(pattern, content):
                import_path = match.group(1)
                resolved = self._resolve_import_path(import_path, base_path)
                if resolved:
                    imports.append(resolved)

        return imports

    def _extract_python_imports(self, content: str, base_path: Path) -> List[Path]:
        """Extract Python imports"""
        imports = []
        import re

        patterns = [
            r"from\s+([^\s]+)\s+import",
            r"import\s+([^\s]+)"
        ]

        for pattern in patterns:
            for match in re.finditer(pattern, content):
                module = match.group(1)
                # Try to resolve to file
                if module.startswith("."):
                    # Relative import
                    rel_path = module.replace(".", "/") + ".py"
                    resolved = self._resolve_python_relative(rel_path, base_path)
                    if resolved:
                        imports.append(resolved)

        return imports

    def _extract_java_imports(self, content: str, base_path: Path) -> List[Path]:
        """Extract Java imports"""
        imports = []
        import re

        pattern = r"import\s+([^\s;]+)\s*;"
        for match in re.finditer(pattern, content):
            package = match.group(1)
            # Convert package to path
            if package.startswith("java.") or package.startswith("javax."):
                continue  # Skip standard library

            # Convert package to file path
            package_path = package.replace(".", "/") + ".java"
            resolved = self._find_java_file(package_path, base_path)
            if resolved:
                imports.append(resolved)

        return imports

    def _extract_csharp_imports(self, content: str, base_path: Path) -> List[Path]:
        """Extract C# using statements"""
        imports = []
        import re

        pattern = r"using\s+([^\s;]+)\s*;"
        for match in re.finditer(pattern, content):
            namespace = match.group(1)
            # Convert namespace to path
            namespace_path = namespace.replace(".", "/") + ".cs"
            resolved = self._find_csharp_file(namespace_path, base_path)
            if resolved:
                imports.append(resolved)

        return imports

    def _extract_go_imports(self, content: str, base_path: Path) -> List[Path]:
        """Extract Go imports"""
        imports = []
        import re

        # Match: "package/path"
        pattern = r'["\']([^"\']+\.go)["\']'
        for match in re.finditer(pattern, content):
            import_path = match.group(1)
            resolved = self._find_go_file(import_path, base_path)
            if resolved:
                imports.append(resolved)

        return imports

    def _resolve_import_path(self, import_path: str, base_path: Path) -> Optional[Path]:
        """Resolve a JavaScript/TypeScript import to a file path"""
        # Handle relative imports
        if import_path.startswith("."):
            # Relative to base_path
            base_dir = base_path.parent
            # Remove extension if present
            if import_path.endswith((".js", ".jsx", ".ts", ".tsx")):
                candidate = base_dir / import_path
            else:
                # Try adding extensions
                for ext in [".js", ".jsx", ".ts", ".tsx", ".json"]:
                    candidate = base_dir / (import_path + ext)
                    if candidate.exists():
                        return candidate
                candidate = base_dir / import_path / "index.js"
                if candidate.exists():
                    return candidate
                return None
        else:
            # Node module - skip
            return None

    def _resolve_python_relative(self, rel_path: str, base_path: Path) -> Optional[Path]:
        """Resolve Python relative import"""
        base_dir = base_path.parent
        candidate = base_dir / rel_path
        if candidate.exists():
            return candidate
        # Try .py extension
        if not rel_path.endswith(".py"):
            candidate = base_dir / (rel_path + ".py")
            if candidate.exists():
                return candidate
        return None

    def _find_java_file(self, package_path: str, base_path: Path) -> Optional[Path]:
        """Find Java file in repository"""
        # Search in common source directories
        search_dirs = [
            base_path.parent,
            base_path.parent / "src" / "main" / "java",
            base_path.parent / "src",
        ]
        for search_dir in search_dirs:
            candidate = search_dir / package_path
            if candidate.exists():
                return candidate
        return None

    def _find_csharp_file(self, namespace_path: str, base_path: Path) -> Optional[Path]:
        """Find C# file in repository"""
        search_dirs = [
            base_path.parent,
            base_path.parent / "Models",
            base_path.parent / "Controllers",
        ]
        for search_dir in search_dirs:
            candidate = search_dir / namespace_path
            if candidate.exists():
                return candidate
        return None

    def _find_go_file(self, import_path: str, base_path: Path) -> Optional[Path]:
        """Find Go file in repository"""
        # Go imports are usually module paths, not file paths
        # For local analysis, we'll skip external modules
        if import_path.startswith(("github.com", "golang.org", "google.golang.org")):
            # Could be external - skip for now
            return None

        # Try to find in repo
        base_dir = base_path.parent
        candidate = base_dir / import_path
        if candidate.exists():
            return candidate
        candidate = base_dir / (import_path + ".go")
        if candidate.exists():
            return candidate
        return None

    def _analyze_file_for_prefix(self, file_path: Path) -> Optional[str]:
        """
        Analyze a file to determine its router prefix.

        Args:
            file_path: Path to file

        Returns:
            Router prefix string or None
        """
        try:
            content = file_path.read_text(encoding="utf-8", errors="ignore")
            language = self.file_scanner.get_language_from_extension(file_path)

            if language in ["javascript", "typescript"]:
                return self._extract_router_prefix_js(content, file_path)
            elif language == "python":
                return self._extract_router_prefix_python(content, file_path)
            elif language == "java":
                return self._extract_router_prefix_java(content, file_path)
            elif language == "csharp":
                return self._extract_router_prefix_csharp(content, file_path)
            elif language == "go":
                return self._extract_router_prefix_go(content, file_path)

            return None

        except Exception as e:
            self.logger.warning("Error analyzing file for prefix", file=str(file_path), error=str(e))
            return None

    def _extract_router_prefix_js(self, content: str, file_path: Path) -> Optional[str]:
        """Extract router prefix from JavaScript/TypeScript"""
        import re

        # Look for: app.use('/api', router) or router.use('/prefix')
        patterns = [
            r"\.use\s*\(\s*['\"`]([^'\"`]+)['\"`]\s*,\s*(?:router|app)",
            r"router\.use\s*\(\s*['\"`]([^'\"`]+)['\"`]",
            r"app\.use\s*\(\s*['\"`]([^'\"`]+)['\"`]\s*,\s*router"
        ]

        for pattern in patterns:
            match = re.search(pattern, content)
            if match:
                prefix = match.group(1)
                return self._normalize_prefix(prefix)

        # Check for module exports that might be routers
        if re.search(r"(?:module\.exports|export\s+default)\s*(?:router|app)", content):
            # This file exports a router - check if it's used with a prefix elsewhere
            # We'll infer from file path
            return self._infer_prefix_from_path(file_path)

        return None

    def _extract_router_prefix_python(self, content: str, file_path: Path) -> Optional[str]:
        """Extract router prefix from Python (Flask Blueprint)"""
        import re

        # Look for: Blueprint('name', __name__, url_prefix='/prefix')
        pattern = r"Blueprint\s*\([^,]*,\s*[^,]*,\s*url_prefix\s*=\s*['\"`]([^'\"`]+)['\"`]"
        match = re.search(pattern, content)
        if match:
            return self._normalize_prefix(match.group(1))

        # Check for app.register_blueprint(bp, url_prefix='/prefix')
        pattern2 = r"register_blueprint\s*\([^,]*,\s*url_prefix\s*=\s*['\"`]([^'\"`]+)['\"`]"
        match2 = re.search(pattern2, content)
        if match2:
            return self._normalize_prefix(match2.group(1))

        return None

    def _extract_router_prefix_java(self, content: str, file_path: Path) -> Optional[str]:
        """Extract router prefix from Java (Spring)"""
        import re

        # Look for: @RequestMapping("/api") at class level
        pattern = r"@RequestMapping\s*\(\s*['\"`]([^'\"`]+)['\"`]"
        match = re.search(pattern, content)
        if match:
            return self._normalize_prefix(match.group(1))

        return None

    def _extract_router_prefix_csharp(self, content: str, file_path: Path) -> Optional[str]:
        """Extract router prefix from C# ASP.NET"""
        import re

        # Look for: [Route("api/[controller]")] on controller class
        pattern = r"\[Route\s*\(\s*['\"`]([^'\"`]+)['\"`]\s*\)\]"
        match = re.search(pattern, content)
        if match:
            prefix = match.group(1)
            # Replace [controller] placeholder
            prefix = re.sub(r'\[controller\]', '', prefix)
            return self._normalize_prefix(prefix)

        return None

    def _extract_router_prefix_go(self, content: str, file_path: Path) -> Optional[str]:
        """Extract router prefix from Go"""
        import re

        # Look for: router.Group("/api") or similar
        pattern = r"(?:router|mux\.Route)\s*\(\s*['\"`]([^'\"`]+)['\"`]"
        match = re.search(pattern, content)
        if match:
            return self._normalize_prefix(match.group(1))

        return None

    def _infer_prefix_from_path(self, file_path: Path) -> Optional[str]:
        """Infer router prefix from file path"""
        # Example: src/api/users/routes.js -> /api/users
        parts = file_path.parts
        try:
            # Find common API directories
            api_idx = -1
            for i, part in enumerate(parts):
                if part.lower() in ["api", "routes", "endpoints", "controllers"]:
                    api_idx = i
                    break

            if api_idx >= 0:
                # Take path from api directory onwards
                prefix_parts = parts[api_idx:]
                # Remove filename and common route file names
                prefix_parts = [p for p in prefix_parts if not p.startswith(".") and p != "index" and p != "routes"]
                if prefix_parts:
                    return "/" + "/".join(prefix_parts)
        except Exception:
            pass

        return None

    def _normalize_prefix(self, prefix: str) -> str:
        """Normalize router prefix"""
        if not prefix:
            return ""

        # Remove query strings
        prefix = prefix.split("?")[0]

        # Ensure starts with /
        if not prefix.startswith("/"):
            prefix = "/" + prefix

        # Normalize slashes
        prefix = re.sub(r"/+", "/", prefix)

        # Remove trailing slash
        if len(prefix) > 1 and prefix.endswith("/"):
            prefix = prefix[:-1]

        return prefix