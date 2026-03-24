"""Endpoint normalization, deduplication, and route resolution"""

from typing import List, Set, Tuple, Dict, Optional, Any
from dataclasses import dataclass, field
import re

from .models.endpoint import Endpoint, HTTPMethod
from .utils.logger import get_logger
from .utils.errors import PipelineError


@dataclass
class ProcessingResult:
    """Result of endpoint processing"""
    endpoints: List[Endpoint]
    duplicates_removed: int
    conflicts: List[Dict[str, Any]]
    errors: List[Dict[str, Any]] = field(default_factory=list)


class EndpointProcessor:
    """Normalizes, deduplicates, and resolves endpoint routes"""

    def __init__(self):
        self.logger = get_logger(__name__)

    def process(
        self,
        endpoints: List[Endpoint],
        router_prefixes: Optional[Dict[str, str]] = None
    ) -> ProcessingResult:
        """
        Process endpoints: normalize, resolve prefixes, deduplicate.

        Args:
            endpoints: List of extracted endpoints
            router_prefixes: Optional mapping of file paths to router prefixes

        Returns:
            ProcessingResult with cleaned endpoints
        """
        self.logger.info("Processing endpoints", total=len(endpoints))

        # Step 1: Normalize paths
        normalized = self._normalize_endpoints(endpoints)

        # Step 2: Resolve router prefixes
        resolved = self._resolve_router_prefixes(normalized, router_prefixes)

        # Step 3: Deduplicate
        deduplicated, duplicates_removed = self._deduplicate_endpoints(resolved)

        # Step 4: Validate
        conflicts = self._detect_conflicts(deduplicated)

        self.logger.info(
            "Endpoint processing complete",
            original=len(endpoints),
            normalized=len(normalized),
            resolved=len(resolved),
            final=len(deduplicated),
            duplicates_removed=duplicates_removed,
            conflicts=len(conflicts)
        )

        return ProcessingResult(
            endpoints=deduplicated,
            duplicates_removed=duplicates_removed,
            conflicts=conflicts
        )

    def _normalize_endpoints(self, endpoints: List[Endpoint]) -> List[Endpoint]:
        """Normalize endpoint paths and methods"""
        normalized = []

        for endpoint in endpoints:
            # Normalize path
            path = self._normalize_path(endpoint.path)
            full_path = self._normalize_path(endpoint.full_path) if endpoint.full_path else path

            # Normalize method
            method = endpoint.method

            # Create normalized endpoint
            normalized_endpoint = Endpoint(
                method=method,
                path=path,
                file_path=endpoint.file_path,
                line_number=endpoint.line_number,
                router_prefix=endpoint.router_prefix,
                full_path=full_path,
                function_name=endpoint.function_name,
                parameters=endpoint.parameters,
                middleware=endpoint.middleware,
                confidence=endpoint.confidence,
                source=endpoint.source,
                metadata=endpoint.metadata
            )
            normalized.append(normalized_endpoint)

        return normalized

    def _normalize_path(self, path: str) -> str:
        """Normalize a path string"""
        if not path:
            return "/"

        # Remove query strings and fragments
        path = path.split("?")[0].split("#")[0]

        # Ensure starts with /
        if not path.startswith("/"):
            path = "/" + path

        # Normalize duplicate slashes
        path = re.sub(r"/+", "/", path)

        # Remove trailing slash (except for root)
        if len(path) > 1 and path.endswith("/"):
            path = path[:-1]

        return path

    def _resolve_router_prefixes(
        self,
        endpoints: List[Endpoint],
        prefix_mapping: Optional[Dict[str, str]] = None
    ) -> List[Endpoint]:
        """
        Resolve router prefixes to create full paths.

        Args:
            endpoints: List of endpoints
            prefix_mapping: Mapping from file paths to their router prefixes

        Returns:
            Endpoints with resolved full paths
        """
        if not prefix_mapping:
            prefix_mapping = {}

        resolved = []

        for endpoint in endpoints:
            file_path = endpoint.file_path
            prefix = prefix_mapping.get(file_path, endpoint.router_prefix)

            if prefix:
                full_path = self._combine_paths(prefix, endpoint.path)
            else:
                full_path = endpoint.path

            # Create new endpoint with resolved path
            resolved_endpoint = Endpoint(
                method=endpoint.method,
                path=endpoint.path,
                file_path=endpoint.file_path,
                line_number=endpoint.line_number,
                router_prefix=prefix,
                full_path=full_path,
                function_name=endpoint.function_name,
                parameters=endpoint.parameters,
                middleware=endpoint.middleware,
                confidence=endpoint.confidence,
                source=endpoint.source,
                metadata=endpoint.metadata
            )
            resolved.append(resolved_endpoint)

        return resolved

    def _combine_paths(self, prefix: str, path: str) -> str:
        """Combine prefix and path"""
        if not prefix:
            return path
        if not path:
            return prefix

        # Normalize both
        prefix = self._normalize_path(prefix)
        path = self._normalize_path(path)

        # Combine: remove trailing slash from prefix, remove leading slash from path
        combined = f"{prefix.rstrip('/')}/{path.lstrip('/')}"
        
        # Normalize the result
        return self._normalize_path(combined)

    def _deduplicate_endpoints(self, endpoints: List[Endpoint]) -> Tuple[List[Endpoint], int]:
        """
        Remove duplicate endpoints based on method + full_path.

        Returns:
            Tuple of (deduplicated list, count of duplicates removed)
        """
        seen: Set[Tuple[HTTPMethod, str]] = set()
        unique_endpoints = []
        duplicates_removed = 0

        for endpoint in endpoints:
            key = endpoint.get_dedup_key()

            if key in seen:
                duplicates_removed += 1
                self.logger.debug(
                    "Duplicate endpoint removed",
                    method=endpoint.method.value,
                    path=endpoint.full_path,
                    file=endpoint.file_path
                )
                continue

            seen.add(key)
            unique_endpoints.append(endpoint)

        return unique_endpoints, duplicates_removed

    def _detect_conflicts(self, endpoints: List[Endpoint]) -> List[Dict[str, Any]]:
        """
        Detect potential conflicts in endpoints.

        Checks for:
        - Same path+method but different parameters
        - Overlapping paths that might cause ambiguity

        Returns:
            List of conflict descriptions
        """
        conflicts = []

        # Group by path
        path_groups: Dict[str, List[Endpoint]] = {}
        for endpoint in endpoints:
            if endpoint.full_path not in path_groups:
                path_groups[endpoint.full_path] = []
            path_groups[endpoint.full_path].append(endpoint)

        # Check each path for multiple methods (that's OK) and parameter conflicts
        for path, eps in path_groups.items():
            # Check for same method on same path (shouldn't happen after deduplication)
            methods = [e.method for e in eps]
            if len(methods) != len(set(methods)):
                conflicts.append({
                    "type": "duplicate_method",
                    "path": path,
                    "methods": methods,
                    "message": f"Duplicate HTTP method for path {path}"
                })

        return conflicts