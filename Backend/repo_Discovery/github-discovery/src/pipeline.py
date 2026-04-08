"""Main pipeline orchestrator"""

import json
from pathlib import Path
from typing import List, Dict, Any, Optional
from dataclasses import dataclass, field
from datetime import datetime
import concurrent.futures

from .config import Config
from .utils.logger import get_logger, setup_logger
from .utils.errors import PipelineError, FileProcessingError, ExtractionError, LLMError
from .utils.cache import Cache
from .repo_manager import RepositoryManager
from .tech_detector import TechStackDetector, TechStack
from .file_scanner import FileScanner
from .ast_parser import TreeSitterParser, ParseResult
from .chunker import ChunkingEngine, Chunk
from .llm_extractor import LLMExtractor, LLMExtractionResult
from .regex_extractor import RegexExtractor
from .endpoint_processor import EndpointProcessor, ProcessingResult
from .route_resolver import RouteResolver
from .models.endpoint import Endpoint


@dataclass
class PipelineStats:
    """Statistics from pipeline execution"""
    start_time: datetime
    end_time: Optional[datetime] = None
    total_files: int = 0
    files_processed: int = 0
    files_failed: int = 0
    endpoints_found: int = 0
    endpoints_from_ast: int = 0
    endpoints_from_regex: int = 0
    endpoints_from_llm: int = 0
    duplicates_removed: int = 0
    llm_calls: int = 0
    llm_failed: int = 0
    cache_hits: int = 0
    errors: List[Dict[str, Any]] = field(default_factory=list)

    def duration(self) -> Optional[float]:
        """Get pipeline duration in seconds"""
        if self.end_time:
            return (self.end_time - self.start_time).total_seconds()
        return None

    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary"""
        return {
            "start_time": self.start_time.isoformat(),
            "end_time": self.end_time.isoformat() if self.end_time else None,
            "duration_seconds": self.duration(),
            "total_files": self.total_files,
            "files_processed": self.files_processed,
            "files_failed": self.files_failed,
            "endpoints_found": self.endpoints_found,
            "endpoints_from_ast": self.endpoints_from_ast,
            "endpoints_from_regex": self.endpoints_from_regex,
            "endpoints_from_llm": self.endpoints_from_llm,
            "duplicates_removed": self.duplicates_removed,
            "llm_calls": self.llm_calls,
            "llm_failed": self.llm_failed,
            "cache_hits": self.cache_hits,
            "errors": self.errors,
        }


class Pipeline:
    """Main pipeline orchestrator"""

    def __init__(self, config: Config):
        self.config = config
        self.logger = get_logger(__name__)
        setup_logger(config.log_level)

        # Initialize components
        self.cache = Cache(ttl=config.cache_ttl)
        self.repo_manager = RepositoryManager(config, self.cache)
        self.file_scanner = FileScanner(Path())  # Will be set per repo
        self.ast_parser = TreeSitterParser()
        self.regex_extractor = RegexExtractor()
        self.chunker = ChunkingEngine(max_chunk_size=config.max_file_size // 100)  # Convert bytes to lines approx
        
        # Initialize LLM extractor (supports both OpenRouter and OpenAI)
        try:
            self.llm_extractor = LLMExtractor(config)
            self.logger.info("LLM extractor initialized")
        except Exception as e:
            self.logger.warning("LLM extractor not available", error=str(e))
            self.llm_extractor = None
            
        self.endpoint_processor = EndpointProcessor()
        self.route_resolver = RouteResolver(Path(), self.file_scanner)  # Will be set per repo

        self.stats = PipelineStats(start_time=datetime.utcnow())

    def run(self, repo_url: str, branch: Optional[str] = None, keep_repo: bool = False) -> Dict[str, Any]:
        """
        Run the complete pipeline.

        Args:
            repo_url: GitHub repository URL
            branch: Optional branch to clone
            keep_repo: Keep cloned repository for debugging

        Returns:
            Dictionary with results and stats

        Raises:
            PipelineError: If pipeline fails
        """
        repo_path = None
        was_cached = False
        
        try:
            self.logger.info("Starting pipeline", repo_url=repo_url, branch=branch)

            # Step 1: Clone repository
            repo_path, was_cached = self.repo_manager.clone_repository(repo_url, branch)
            self.file_scanner.repo_path = repo_path
            self.route_resolver.repo_path = repo_path
            self.route_resolver.file_scanner = self.file_scanner

            # Step 2: Detect tech stack
            self.logger.info("Detecting tech stack", repo_path=str(repo_path))
            tech_detector = TechStackDetector(repo_path)
            tech_stack = tech_detector.detect()

            # Step 3: Scan files
            self.logger.info("Scanning files")
            candidate_files = self.file_scanner.scan_files()
            self.stats.total_files = len(candidate_files)

            # Step 4: Resolve route prefixes
            self.logger.info("Resolving route prefixes")
            prefix_mapping = self.route_resolver.resolve_prefixes(candidate_files)

            # Step 5: Parse files (AST → Regex → LLM)
            self.logger.info("Parsing files for endpoints")
            all_endpoints = self._parse_files(candidate_files, tech_stack, prefix_mapping)

            # Step 6: Process endpoints (normalize, deduplicate)
            self.logger.info("Processing endpoints")
            processing_result = self.endpoint_processor.process(all_endpoints, prefix_mapping)
            final_endpoints = processing_result.endpoints

            # Update stats
            self.stats.files_processed = self.stats.total_files - self.stats.files_failed
            self.stats.endpoints_found = len(final_endpoints)
            self.stats.duplicates_removed = processing_result.duplicates_removed
            self.stats.end_time = datetime.utcnow()

            openapi_spec = None
            openapi_dict: Optional[dict] = None
            if getattr(self.config, "output_openapi", False):
                # Step 7: Generate OpenAPI spec (optional)
                self.logger.info("Generating OpenAPI specification")
                from .models.openapi import OpenAPISpec  # local import for endpoints-only mode

                openapi_spec = OpenAPISpec.from_endpoints(
                    endpoints=final_endpoints,
                    title=f"API Specification - {repo_path.name}",
                    description=f"Auto-generated from repository {repo_url}",
                    version="1.0.0",
                )
                openapi_dict = openapi_spec.to_dict()

            # Step 8: Save outputs (OpenAPI optional)
            self._save_outputs(openapi_spec, all_endpoints, repo_path.name)

            # Return results (cleanup happens in finally block)
            result = {
                "success": True,
                "repo_url": repo_url,
                "repo_cached": was_cached,
                "openapi": openapi_dict,
                "raw_endpoints": [e.to_dict() for e in all_endpoints],
                "final_endpoints": [e.to_dict() for e in final_endpoints],
                "stats": self.stats.to_dict(),
                "tech_stack": {
                    "languages": list(tech_stack.languages),
                    "frameworks": tech_stack.frameworks,
                    "has_graphql": tech_stack.has_graphql,
                    "has_rest": tech_stack.has_rest,
                }
            }

            self.logger.info("Pipeline completed successfully", endpoints=len(final_endpoints))
            return result

        except Exception as e:
            self.logger.error("Pipeline failed", error=str(e))
            self.stats.end_time = datetime.utcnow()
            raise PipelineError(f"Pipeline failed: {str(e)}") from e

        finally:
            # Ensure cleanup happens in all cases (success or failure)
            # Keep repo only if explicitly requested OR if it was cached (to reuse later)
            should_cleanup = repo_path and not keep_repo and not was_cached
            
            if should_cleanup:
                self.logger.info("Cleaning up repository", repo_path=str(repo_path))
                try:
                    self.repo_manager.cleanup_repository(repo_path, keep=False)
                    self.logger.info("Repository cleanup completed")
                except Exception as e:
                    self.logger.error(
                        "Repository cleanup failed - attempting force cleanup",
                        repo_path=str(repo_path),
                        error=str(e)
                    )
                    try:
                        # Force cleanup with more aggressive approach
                        import shutil
                        import gc
                        
                        # Release any file handles
                        gc.collect()
                        
                        # Windows: retry cleanup with force
                        if repo_path.exists():
                            shutil.rmtree(repo_path, ignore_errors=True)
                            self.logger.info("Force cleanup completed", repo_path=str(repo_path))
                    except Exception as force_error:
                        self.logger.warning(
                            "Force cleanup also failed - repository may need manual cleanup",
                            repo_path=str(repo_path),
                            error=str(force_error)
                        )
            elif keep_repo:
                self.logger.info("Repository kept for debugging", repo_path=str(repo_path))
            elif was_cached:
                self.logger.info("Repository kept for cache reuse", repo_path=str(repo_path))


    def run_on_path(self, *, repo_path: Path, repo_url: str, repo_name: Optional[str] = None) -> Dict[str, Any]:
        """Run the pipeline on an existing local checkout (no cloning).

        Args:
            repo_path: Path to an existing repository checkout
            repo_url: A label for logging/metadata (can be a git URL or local path)
            repo_name: Optional stable name prefix for output files
        """

        if not repo_path.exists():
            raise PipelineError(f"Repository path does not exist: {repo_path}")

        self.stats = PipelineStats(start_time=datetime.utcnow())

        self.logger.info("Starting pipeline (local path)", repo_path=str(repo_path))

        # Wire paths
        self.file_scanner.repo_path = repo_path
        self.route_resolver.repo_path = repo_path
        self.route_resolver.file_scanner = self.file_scanner

        # Step 2: Detect tech stack
        self.logger.info("Detecting tech stack", repo_path=str(repo_path))
        tech_detector = TechStackDetector(repo_path)
        tech_stack = tech_detector.detect()

        # Step 3: Scan files
        self.logger.info("Scanning files")
        candidate_files = self.file_scanner.scan_files()
        self.stats.total_files = len(candidate_files)

        # Step 4: Resolve route prefixes
        self.logger.info("Resolving route prefixes")
        prefix_mapping = self.route_resolver.resolve_prefixes(candidate_files)

        # Step 5: Parse files
        self.logger.info("Parsing files for endpoints")
        all_endpoints = self._parse_files(candidate_files, tech_stack, prefix_mapping)

        # Step 6: Process endpoints
        self.logger.info("Processing endpoints")
        processing_result = self.endpoint_processor.process(all_endpoints, prefix_mapping)
        final_endpoints = processing_result.endpoints

        # Update stats
        self.stats.files_processed = self.stats.total_files - self.stats.files_failed
        self.stats.endpoints_found = len(final_endpoints)
        self.stats.duplicates_removed = processing_result.duplicates_removed
        self.stats.end_time = datetime.utcnow()

        openapi_spec = None
        openapi_dict: Optional[dict] = None
        if getattr(self.config, "output_openapi", False):
            # Step 7: Generate OpenAPI (optional)
            self.logger.info("Generating OpenAPI specification")
            from .models.openapi import OpenAPISpec  # local import

            openapi_spec = OpenAPISpec.from_endpoints(
                endpoints=final_endpoints,
                title=f"API Specification - {repo_path.name}",
                description=f"Auto-generated from repository {repo_url}",
                version="1.0.0",
            )
            openapi_dict = openapi_spec.to_dict()

        # Step 8: Save outputs
        out_name = repo_name or repo_path.name
        self._save_outputs(openapi_spec, all_endpoints, out_name)

        result = {
            "success": True,
            "repo_url": repo_url,
            "repo_cached": False,
            "openapi": openapi_dict,
            "raw_endpoints": [e.to_dict() for e in all_endpoints],
            "final_endpoints": [e.to_dict() for e in final_endpoints],
            "stats": self.stats.to_dict(),
            "tech_stack": {
                "languages": list(tech_stack.languages),
                "frameworks": tech_stack.frameworks,
                "has_graphql": tech_stack.has_graphql,
                "has_rest": tech_stack.has_rest,
            },
        }

        self.logger.info("Pipeline completed successfully (local path)", endpoints=len(final_endpoints))
        return result

    def _parse_files(
        self,
        file_paths: List[Path],
        tech_stack: TechStack,
        prefix_mapping: Dict[str, str]
    ) -> List[Endpoint]:
        """
        Parse all candidate files using three-stage pipeline: AST → Regex → LLM.

        Args:
            file_paths: List of file paths to parse
            tech_stack: Detected tech stack
            prefix_mapping: Router prefix mapping

        Returns:
            List of all extracted endpoints
        """
        all_endpoints = []
        file_infos = []

        # Prepare file info list
        for file_path in file_paths:
            language = self.file_scanner.get_language_from_extension(file_path)
            router_prefix = prefix_mapping.get(str(file_path))

            file_infos.append({
                "path": file_path,
                "language": language,
                "router_prefix": router_prefix
            })

        # ========== STAGE 1: AST PARSING ==========
        self.logger.info("Stage 1: AST parsing", count=len(file_infos))
        ast_results = self.ast_parser.parse_files_parallel(file_infos)

        # Collect AST endpoints and track files with low endpoint counts
        files_needing_regex = []
        
        for result in ast_results:
            all_endpoints.extend(result.endpoints)
            self.stats.endpoints_from_ast += len(result.endpoints)
            
            if result.errors:
                self.stats.errors.extend(result.errors)
                self.stats.files_failed += 1
            
            # Track files with few/no endpoints for regex fallback
            if len(result.endpoints) < 2:  # Files with 0-1 endpoints need regex
                files_needing_regex.append(result)

        self.logger.info("Stage 1 complete", ast_endpoints=len(all_endpoints), files_needing_regex=len(files_needing_regex))

        # ========== STAGE 2: REGEX EXTRACTION (Fallback) ==========
        self.logger.info("Stage 2: Regex extraction for files with few endpoints", count=len(files_needing_regex))
        
        for parse_result in files_needing_regex:
            try:
                file_path = Path(parse_result.file_path) if isinstance(parse_result.file_path, str) else parse_result.file_path
                language = self.file_scanner.get_language_from_extension(file_path)
                
                if language not in ['javascript', 'typescript', 'js', 'ts', 'jsx', 'tsx', 'python', 'py', 'java']:
                    continue
                
                # Read file content
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    code = f.read()
                
                # Extract using regex
                regex_endpoints = self.regex_extractor.extract_endpoints(code, file_path, language)
                
                # Filter out duplicates with AST results
                new_endpoints = []
                for ep in regex_endpoints:
                    # Check if this endpoint is already found by AST
                    is_duplicate = any(
                        ast_ep.method == ep.method and ast_ep.path == ep.path
                        for ast_ep in all_endpoints
                        if ast_ep.file_path == str(file_path)
                    )
                    if not is_duplicate:
                        new_endpoints.append(ep)
                
                all_endpoints.extend(new_endpoints)
                self.stats.endpoints_from_regex += len(new_endpoints)
                
                if new_endpoints:
                    self.logger.info(
                        "Regex extraction found new endpoints",
                        file=file_path.name,
                        count=len(new_endpoints)
                    )
                
            except Exception as e:
                self.logger.warning("Regex extraction failed for file", file=str(file_path), error=str(e))

        self.logger.info("Stage 2 complete", regex_endpoints=self.stats.endpoints_from_regex)

        # ========== STAGE 3: LLM EXTRACTION (High confidence) ==========
        if self.llm_extractor and self.llm_extractor.is_available():
            self.logger.info("Stage 3: LLM extraction for low-confidence endpoints")
            
            # Find endpoints below confidence threshold
            low_confidence_by_file: Dict[str, List[Endpoint]] = {}
            for ep in all_endpoints:
                if ep.confidence < self.config.llm_confidence_threshold:
                    file_key = ep.file_path
                    if file_key not in low_confidence_by_file:
                        low_confidence_by_file[file_key] = []
                    low_confidence_by_file[file_key].append(ep)
            
            self.logger.info("Files with low-confidence endpoints", count=len(low_confidence_by_file))
            
            # Process files with low-confidence endpoints using LLM
            for file_path_str, low_conf_endpoints in low_confidence_by_file.items():
                try:
                    file_path = Path(file_path_str)
                    language = self.file_scanner.get_language_from_extension(file_path)
                    router_prefix = prefix_mapping.get(str(file_path))
                    
                    # Read file content
                    with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                        code = f.read()
                    
                    # Don't send files that are too large to LLM
                    if len(code) > 10000:
                        self.logger.debug("File too large for LLM", file=file_path.name, size=len(code))
                        continue
                    
                    # Call LLM
                    self.logger.debug("Calling LLM for file", file=file_path.name)
                    llm_result = self.llm_extractor.extract_endpoints(code, file_path, language, router_prefix)
                    self.stats.llm_calls += 1
                    
                    # Add high-confidence LLM endpoints
                    for ep in llm_result.endpoints:
                        if ep.confidence >= self.config.llm_confidence_threshold:
                            # Check if duplicate
                            is_duplicate = any(
                                e.method == ep.method and e.path == ep.path
                                for e in all_endpoints
                                if e.file_path == str(file_path)
                            )
                            if not is_duplicate:
                                all_endpoints.append(ep)
                                self.stats.endpoints_from_llm += 1
                    
                    self.logger.info(
                        "LLM extraction complete",
                        file=file_path.name,
                        llm_endpoints=len(llm_result.endpoints),
                        high_confidence=len([e for e in llm_result.endpoints if e.confidence >= self.config.llm_confidence_threshold])
                    )
                    
                except LLMError as e:
                    self.stats.llm_failed += 1
                    self.logger.warning("LLM extraction failed", file=file_path_str, error=str(e))
                except Exception as e:
                    self.stats.llm_failed += 1
                    self.logger.error("Unexpected error in LLM extraction", file=file_path_str, error=str(e))
            
            self.logger.info("Stage 3 complete", llm_endpoints=self.stats.endpoints_from_llm, llm_calls=self.stats.llm_calls)
        else:
            self.logger.info("Stage 3 skipped: LLM extractor not available")

        self.stats.files_processed = len(file_infos) - self.stats.files_failed
        return all_endpoints

    def _save_outputs(self, openapi_spec: Any, endpoints: List[Endpoint], repo_name: str) -> None:
        """Save outputs to files"""
        output_dir = Path(self.config.output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)

        if openapi_spec is not None:
            # Save OpenAPI JSON
            openapi_path = output_dir / f"{repo_name}_openapi.json"
            openapi_spec.save(str(openapi_path))
            self.logger.info("OpenAPI spec saved", path=str(openapi_path))

        # Save raw endpoints
        endpoints_path = output_dir / f"{repo_name}_endpoints.json"
        with open(endpoints_path, "w", encoding="utf-8") as f:
            json.dump([e.to_dict() for e in endpoints], f, indent=2, ensure_ascii=False)
        self.logger.info("Raw endpoints saved", path=str(endpoints_path))

        # Save stats
        stats_path = output_dir / f"{repo_name}_stats.json"
        with open(stats_path, "w", encoding="utf-8") as f:
            json.dump(self.stats.to_dict(), f, indent=2, ensure_ascii=False)
        self.logger.info("Stats saved", path=str(stats_path))


def run_pipeline(repo_url: str, branch: Optional[str] = None, keep_repo: bool = False) -> Dict[str, Any]:
    """
    Convenience function to run the pipeline.

    Args:
        repo_url: GitHub repository URL
        branch: Optional branch to clone
        keep_repo: Keep cloned repository for debugging

    Returns:
        Dictionary with results and stats
    """
    config = Config()
    pipeline = Pipeline(config)
    return pipeline.run(repo_url, branch, keep_repo)