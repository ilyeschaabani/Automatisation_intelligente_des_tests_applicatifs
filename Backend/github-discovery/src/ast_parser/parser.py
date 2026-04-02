"""Tree-sitter based AST parser for multiple languages.

This module is designed to be robust in CI/offline environments.

If `tree-sitter-language-pack` cannot download prebuilt parsers (e.g. network
restricted), AST parsing is automatically disabled *per language* and the
pipeline continues using regex/spec/config layers.
"""

import os
import sys
import threading
from pathlib import Path
from typing import Optional, List, Dict, Any
from dataclasses import dataclass

import tree_sitter
from tree_sitter import Language, Parser

from ..models.endpoint import Endpoint, HTTPMethod
from ..utils.logger import get_logger
from ..utils.errors import ASTError
from .node_visitors import get_visitor_for_language


@dataclass
class ParseResult:
    """Result of AST parsing"""
    endpoints: List[Endpoint]
    errors: List[Dict[str, Any]]
    file_path: Path
    language: str


class TreeSitterParser:
    """AST parser using Tree-sitter for multiple languages"""

    # Process-wide cache to avoid repeated download attempts/log spam
    _get_parser_lock = threading.Lock()
    _disabled_langs: set = set()
    _disabled_reasons: Dict[str, str] = {}
    _warned_disabled: set = set()

    # Language to Tree-sitter language name mapping
    LANGUAGE_MAPPING = {
        "javascript": "javascript",
        "typescript": "typescript",
        "python": "python",
        "java": "java",
        "csharp": "csharp",
        "go": "go",
        "ruby": "ruby",
        "php": "php",
    }

    def __init__(self):
        self.logger = get_logger(__name__)
        self.parser = Parser()
        self.languages: Dict[str, Language] = {}
        self._initialize_languages()

    def _initialize_languages(self) -> None:
        """Initialize Tree-sitter languages from installed packages"""
        if os.getenv("AST_DISABLE", "").strip().lower() in {"1", "true", "yes", "y", "on"}:
            self.use_language_pack = False
            self.ast_disabled = True
            self.logger.info("AST parsing disabled via AST_DISABLE")
            return

        try:
            # Try to import tree_sitter_language_pack
            from tree_sitter_language_pack import get_parser

            # Allow running in offline environments (no downloads)
            if os.getenv("AST_OFFLINE", "").strip().lower() in {"1", "true", "yes", "y", "on"} or os.getenv(
                "AST_DISABLE_LANGUAGE_PACK", ""
            ).strip().lower() in {"1", "true", "yes", "y", "on"}:
                self.use_language_pack = False
                self.ast_disabled = True
                self.logger.info("AST parsing disabled (offline / language-pack disabled)")
                return

            self.get_parser = get_parser
            self.use_language_pack = True
            self.ast_disabled = False
            self.logger.info("Using tree-sitter-language-pack for parsers")
        except ImportError:
            self.logger.warning("tree-sitter-language-pack not available, using built-in parsers only")
            self.use_language_pack = False
            self.ast_disabled = True
            self._build_builtin_languages()

    def _get_ts_parser(self, tree_sitter_lang_name: str, language: str) -> Parser:
        """Get a parser for a language, disabling that language on repeated failures."""
        if getattr(self, "ast_disabled", False):
            raise ASTError("AST parsing is disabled")

        disabled_env = os.getenv("AST_DISABLE_LANGUAGES", "")
        if disabled_env:
            disabled = {x.strip().lower() for x in disabled_env.split(",") if x.strip()}
            if language.lower() in disabled or tree_sitter_lang_name.lower() in disabled:
                raise ASTError(f"AST parsing disabled for {language} via AST_DISABLE_LANGUAGES")

        if tree_sitter_lang_name in TreeSitterParser._disabled_langs:
            reason = TreeSitterParser._disabled_reasons.get(tree_sitter_lang_name, "unknown")
            raise ASTError(f"AST parsing disabled for {language}: {reason}")

        if not self.use_language_pack:
            raise ASTError("AST parsing unavailable (language pack disabled)")

        # Serialize get_parser calls because language-pack may download/build on demand.
        with TreeSitterParser._get_parser_lock:
            if tree_sitter_lang_name in TreeSitterParser._disabled_langs:
                reason = TreeSitterParser._disabled_reasons.get(tree_sitter_lang_name, "unknown")
                raise ASTError(f"AST parsing disabled for {language}: {reason}")
            try:
                return self.get_parser(tree_sitter_lang_name)
            except Exception as e:
                reason = str(e)
                TreeSitterParser._disabled_langs.add(tree_sitter_lang_name)
                TreeSitterParser._disabled_reasons[tree_sitter_lang_name] = reason
                if tree_sitter_lang_name not in TreeSitterParser._warned_disabled:
                    TreeSitterParser._warned_disabled.add(tree_sitter_lang_name)
                    self.logger.warning(
                        "Disabling AST parsing for language due to parser init failure",
                        language=language,
                        tree_sitter_language=tree_sitter_lang_name,
                        reason=reason,
                    )
                raise ASTError(f"Failed to get parser for {language}: {reason}") from e

    def _build_builtin_languages(self) -> None:
        """Build Tree-sitter languages from source (requires git submodules)"""
        # This would require cloning tree-sitter grammars
        # For production, we rely on tree-sitter-language-pack
        self.logger.warning("Built-in language loading not implemented - install tree-sitter-language-pack")

    def parse_file(self, file_path: Path, language: str, content: str = None, router_prefix: str = None) -> ParseResult:
        """
        Parse a single file and extract endpoints.

        Args:
            file_path: Path to file
            language: Programming language
            content: Optional file content (will read if not provided)
            router_prefix: Optional router prefix to apply

        Returns:
            ParseResult with extracted endpoints

        Raises:
            ASTError: If parsing fails
        """
        try:
            if content is None:
                content = file_path.read_text(encoding="utf-8", errors="ignore")

            # Get or create parser for language
            tree_sitter_lang_name = self.LANGUAGE_MAPPING.get(language)
            if not tree_sitter_lang_name:
                raise ASTError(f"Unsupported language: {language}")

            # Get the Tree-sitter parser (language-pack) or fail fast when unavailable
            parser = self._get_ts_parser(tree_sitter_lang_name, language)

            # Parse the content
            tree = parser.parse(bytes(content, "utf8"))

            # Get appropriate visitor
            visitor_class = get_visitor_for_language(language)
            if not visitor_class:
                raise ASTError(f"No visitor implemented for language: {language}")

            visitor = visitor_class(
                file_path=file_path,
                router_prefix=router_prefix,
                logger=self.logger
            )

            # Visit the AST
            visitor.visit(tree.root_node)

            # Collect endpoints
            endpoints = visitor.endpoints

            self.logger.info(
                "File parsed successfully",
                file=str(file_path),
                language=language,
                endpoints_found=len(endpoints)
            )

            return ParseResult(
                endpoints=endpoints,
                errors=visitor.errors,
                file_path=file_path,
                language=language
            )

        except ASTError:
            # Let callers decide how to degrade (parallel parser converts these to ParseResult errors)
            raise
        except Exception as e:
            self.logger.error("Failed to parse file", file=str(file_path), language=language, error=str(e))
            raise ASTError(f"Failed to parse {file_path}: {str(e)}") from e

    def parse_files_parallel(self, file_infos: List[Dict[str, Any]]) -> List[ParseResult]:
        """
        Parse multiple files in parallel using threading.

        Args:
            file_infos: List of dicts with keys: path, language, router_prefix (optional)

        Returns:
            List of ParseResults
        """
        from concurrent.futures import ThreadPoolExecutor, as_completed
        from ..config import Config

        config = Config()
        results = []

        with ThreadPoolExecutor(max_workers=config.max_workers) as executor:
            futures = {
                executor.submit(self._parse_file_worker, info): info
                for info in file_infos
            }

            for future in as_completed(futures):
                info = futures[future]
                try:
                    result = future.result()
                    results.append(result)
                except Exception as e:
                    # Create error result (no noisy logs here; parser init failures are logged once per language)
                    results.append(ParseResult(
                        endpoints=[],
                        errors=[{"file": str(info["path"]), "error": str(e)}],
                        file_path=info["path"],
                        language=info["language"]
                    ))

        return results

    def _parse_file_worker(self, info: Dict[str, Any]) -> ParseResult:
        """Worker function for parallel parsing.

        We reuse the same TreeSitterParser instance to avoid repeatedly downloading
        language-pack parsers. Each `get_parser(...)` call returns a parser instance,
        so parsing remains thread-safe.
        """
        try:
            return self.parse_file(
                file_path=info["path"],
                language=info["language"],
                content=info.get("content"),
                router_prefix=info.get("router_prefix"),
            )
        except ASTError as e:
            return ParseResult(
                endpoints=[],
                errors=[{"file": str(info["path"]), "error": str(e)}],
                file_path=info["path"],
                language=info["language"],
            )