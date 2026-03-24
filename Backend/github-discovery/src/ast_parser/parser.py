"""Tree-sitter based AST parser for multiple languages"""

import sys
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
        try:
            # Try to import tree_sitter_language_pack
            from tree_sitter_language_pack import get_parser

            self.get_parser = get_parser
            self.use_language_pack = True
            self.logger.info("Using tree-sitter-language-pack for parsers")
        except ImportError:
            self.logger.warning("tree-sitter-language-pack not available, using built-in parsers only")
            self.use_language_pack = False
            self._build_builtin_languages()

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

            # Get the Tree-sitter language
            if self.use_language_pack:
                try:
                    parser = self.get_parser(tree_sitter_lang_name)
                except Exception as e:
                    raise ASTError(f"Failed to get parser for {language}: {str(e)}") from e
            else:
                if tree_sitter_lang_name not in self.languages:
                    raise ASTError(f"Language {language} not loaded")
                parser = self.parser
                parser.set_language(self.languages[tree_sitter_lang_name])

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
                    self.logger.error(
                        "Parallel parse failed",
                        file=str(info["path"]),
                        language=info["language"],
                        error=str(e)
                    )
                    # Create error result
                    results.append(ParseResult(
                        endpoints=[],
                        errors=[{"file": str(info["path"]), "error": str(e)}],
                        file_path=info["path"],
                        language=info["language"]
                    ))

        return results

        return results

    def _parse_file_worker(self, info: Dict[str, Any]) -> ParseResult:
        """Worker function for parallel parsing - creates new parser per thread for thread safety"""
        # Re-initialize parser in each thread to ensure thread-safe parsing
        parser = TreeSitterParser()
        return parser.parse_file(
            file_path=info["path"],
            language=info["language"],
            content=info.get("content"),
            router_prefix=info.get("router_prefix")
        )