"""Layered endpoint discovery.

Implements the 4-layer discovery approach:
1) Spec-first import (OpenAPI/Swagger/Postman/Insomnia)
2) Config routes parsing (framework config files)
3) Code routes (AST/regex per framework) - orchestrated by Pipeline
4) Universal fallback heuristics (language-agnostic)
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple
from urllib.parse import urlparse

import yaml

from .models.endpoint import Endpoint, HTTPMethod
from .utils.logger import get_logger


# Keep this focused: ignore heavy/vendor dirs but DO scan docs/ for specs.
IGNORED_DIRS: set[str] = {
    "node_modules",
    "venv",
    ".venv",
    "env",
    ".env",
    "__pycache__",
    "build",
    "dist",
    "target",
    "bin",
    "obj",
    ".git",
    ".idea",
    ".vscode",
    "coverage",
    ".coverage",
}


def _iter_repo_files(repo_path: Path) -> Iterable[Path]:
    for file_path in repo_path.rglob("*"):
        if not file_path.is_file():
            continue
        if any(part in IGNORED_DIRS for part in file_path.parts):
            continue
        if file_path.name.startswith("."):
            continue
        yield file_path


def _safe_read_text(file_path: Path, *, max_bytes: int = 200_000) -> Optional[str]:
    try:
        size = file_path.stat().st_size
        if size > max_bytes:
            return None
    except OSError:
        return None

    try:
        data = file_path.read_bytes()
    except OSError:
        return None

    # Binary-ish guard
    if b"\x00" in data:
        return None

    try:
        return data.decode("utf-8", errors="ignore")
    except Exception:
        return None


def _normalize_candidate_path(path: str) -> Optional[str]:
    if not path:
        return None

    path = path.strip()
    # Ignore common filesystem paths that create noise.
    for bad_prefix in ("/usr/", "/var/", "/etc/", "/home/"):
        if path.startswith(bad_prefix):
            return None

    # Trim surrounding quotes/backticks.
    path = path.strip("\"'`")
    # Remove query/fragment.
    path = path.split("?", 1)[0].split("#", 1)[0]

    if not path.startswith("/"):
        # Treat as a relative route path (e.g., "api/login" -> "/api/login").
        # URL/base URL stripping belongs in _extract_path_from_url().
        path = "/" + path

    # Avoid extremely short/noisy paths
    if len(path) < 2:
        return "/"
    return path


def _httpmethod_from_string(method: str) -> Optional[HTTPMethod]:
    if not method:
        return None
    m = method.strip().lower()
    try:
        return HTTPMethod(m)
    except Exception:
        return None


def _extract_path_from_url(raw_url: str) -> Optional[str]:
    if not raw_url:
        return None

    raw_url = raw_url.strip()

    # Try parsing as a URL first.
    parsed = urlparse(raw_url)
    if parsed.scheme and parsed.path:
        return _normalize_candidate_path(parsed.path)

    # Handle template base URLs like {{baseUrl}}/api/users
    if "/" in raw_url:
        idx = raw_url.find("/")
        return _normalize_candidate_path(raw_url[idx:])

    return _normalize_candidate_path(raw_url)


@dataclass
class SpecFirstResult:
    """Result of spec-first import."""

    endpoints: List[Endpoint]
    openapi: Optional[Dict[str, Any]] = None
    source_file: Optional[str] = None
    short_circuit: bool = False


class SpecFirstExtractor:
    """Layer 1: Import existing API descriptions."""

    def __init__(self) -> None:
        self.logger = get_logger(__name__)

    def try_extract(self, repo_path: Path) -> Optional[SpecFirstResult]:
        candidates = list(self._find_spec_candidates(repo_path))
        if not candidates:
            return None

        # Prefer OpenAPI/Swagger over Postman/Insomnia
        openapi_like = [p for p in candidates if self._is_openapi_or_swagger_name(p.name)]
        postman_like = [p for p in candidates if self._is_postman_name(p.name)]
        insomnia_like = [p for p in candidates if self._is_insomnia_name(p.name)]

        for file_path in openapi_like + postman_like + insomnia_like:
            try:
                result = self._parse_spec_file(file_path)
                if result:
                    self.logger.info("Spec-first import succeeded", file=str(file_path), endpoints=len(result.endpoints))
                    return result
            except Exception as e:
                self.logger.debug("Spec-first import failed for candidate", file=str(file_path), error=str(e))

        return None

    def _find_spec_candidates(self, repo_path: Path) -> Iterable[Path]:
        for p in _iter_repo_files(repo_path):
            name = p.name.lower()
            if name.endswith((".json", ".yaml", ".yml")) and (
                "openapi" in name
                or "swagger" in name
                or "postman" in name
                or "insomnia" in name
            ):
                yield p
            # Common Postman naming
            if name.endswith(".postman_collection.json"):
                yield p
            if name in {"postman_collection.json", "insomnia.json"}:
                yield p

    def _is_openapi_or_swagger_name(self, filename: str) -> bool:
        f = filename.lower()
        return ("openapi" in f or "swagger" in f) and f.endswith((".json", ".yaml", ".yml"))

    def _is_postman_name(self, filename: str) -> bool:
        f = filename.lower()
        return "postman" in f and f.endswith(".json")

    def _is_insomnia_name(self, filename: str) -> bool:
        f = filename.lower()
        return "insomnia" in f and f.endswith(".json")

    def _parse_spec_file(self, file_path: Path) -> Optional[SpecFirstResult]:
        if file_path.suffix.lower() in {".yaml", ".yml"}:
            raw = file_path.read_text(encoding="utf-8", errors="ignore")
            data = yaml.safe_load(raw)
        else:
            data = json.loads(file_path.read_text(encoding="utf-8", errors="ignore") or "{}")

        if not isinstance(data, dict):
            return None

        # OpenAPI/Swagger
        if ("openapi" in data or "swagger" in data) and isinstance(data.get("paths"), dict):
            endpoints = self._endpoints_from_openapi_dict(data, file_path)
            return SpecFirstResult(
                endpoints=endpoints,
                openapi=data,
                source_file=str(file_path),
                short_circuit=True,
            )

        # Postman
        if self._looks_like_postman(data):
            endpoints = self._endpoints_from_postman_dict(data, file_path)
            return SpecFirstResult(
                endpoints=endpoints,
                openapi=None,
                source_file=str(file_path),
                short_circuit=True,
            )

        # Insomnia
        if self._looks_like_insomnia(data):
            endpoints = self._endpoints_from_insomnia_dict(data, file_path)
            return SpecFirstResult(
                endpoints=endpoints,
                openapi=None,
                source_file=str(file_path),
                short_circuit=True,
            )

        return None

    def _endpoints_from_openapi_dict(self, spec: Dict[str, Any], file_path: Path) -> List[Endpoint]:
        endpoints: List[Endpoint] = []
        paths = spec.get("paths") or {}

        def _coerce_schema(obj: Any) -> Optional[Dict[str, Any]]:
            if isinstance(obj, dict):
                return obj
            return None

        def _extract_param_list(params: Any, *, location: str) -> List[Dict[str, Any]]:
            out: List[Dict[str, Any]] = []
            if not isinstance(params, list):
                return out
            for p in params:
                if not isinstance(p, dict):
                    continue
                if p.get("in") != location:
                    continue
                name = p.get("name")
                if not isinstance(name, str) or not name:
                    continue
                schema = _coerce_schema(p.get("schema")) or {"type": "string"}
                item: Dict[str, Any] = {
                    "name": name,
                    "required": bool(p.get("required", location == "path")),
                    "schema": schema,
                }
                if "example" in p:
                    item["example"] = p.get("example")
                out.append(item)
            return out

        def _extract_request_body(body_obj: Any) -> Optional[Dict[str, Any]]:
            if not isinstance(body_obj, dict):
                return None
            content = body_obj.get("content")
            if not isinstance(content, dict) or not content:
                return None
            # Prefer JSON if present
            content_type = "application/json" if "application/json" in content else next(iter(content.keys()))
            media = content.get(content_type)
            if not isinstance(media, dict):
                return None
            schema = _coerce_schema(media.get("schema")) or {"type": "object"}
            out: Dict[str, Any] = {
                "param": None,
                "dto_type": None,
                "content_type": content_type,
                "required": bool(body_obj.get("required", True)),
                "schema": schema,
            }
            if "example" in media:
                out["example"] = media.get("example")
            return out

        for path, methods in paths.items():
            if not isinstance(path, str) or not isinstance(methods, dict):
                continue
            normalized_path = _normalize_candidate_path(path)
            if not normalized_path:
                continue

            path_level_params = []
            if isinstance(methods.get("parameters"), list):
                path_level_params = methods.get("parameters") or []

            for method_key, operation in methods.items():
                if not isinstance(method_key, str):
                    continue
                if method_key.lower().startswith("x-"):
                    continue
                if method_key == "parameters":
                    continue
                http_method = _httpmethod_from_string(method_key)
                if not http_method:
                    continue

                function_name = None
                if isinstance(operation, dict):
                    function_name = operation.get("operationId") or operation.get("summary")

                request_meta: Dict[str, Any] = {"path": [], "query": [], "header": [], "body": None}
                if isinstance(operation, dict):
                    op_params = operation.get("parameters")
                    combined_params = []
                    if isinstance(path_level_params, list):
                        combined_params.extend(path_level_params)
                    if isinstance(op_params, list):
                        combined_params.extend(op_params)

                    request_meta["path"].extend(_extract_param_list(combined_params, location="path"))
                    request_meta["query"].extend(_extract_param_list(combined_params, location="query"))
                    request_meta["header"].extend(_extract_param_list(combined_params, location="header"))

                    body = _extract_request_body(operation.get("requestBody"))
                    if body is not None:
                        request_meta["body"] = body

                endpoints.append(
                    Endpoint(
                        method=http_method,
                        path=normalized_path,
                        file_path=str(file_path),
                        line_number=0,
                        confidence=1.0,
                        source="spec",
                        function_name=function_name,
                        metadata={
                            "spec_file": str(file_path),
                            "request": request_meta,
                        },
                    )
                )

        return endpoints

    def _looks_like_postman(self, data: Dict[str, Any]) -> bool:
        info = data.get("info")
        if isinstance(info, dict) and isinstance(info.get("schema"), str) and "postman" in info["schema"].lower():
            return True
        return isinstance(data.get("item"), list)

    def _endpoints_from_postman_dict(self, data: Dict[str, Any], file_path: Path) -> List[Endpoint]:
        endpoints: List[Endpoint] = []

        def walk_items(items: Sequence[Dict[str, Any]]) -> None:
            for it in items:
                if not isinstance(it, dict):
                    continue
                if isinstance(it.get("item"), list):
                    walk_items(it["item"])
                    continue
                req = it.get("request")
                if not isinstance(req, dict):
                    continue

                method = _httpmethod_from_string(req.get("method") or "") or HTTPMethod.GET
                url = req.get("url")
                raw_url = None
                if isinstance(url, str):
                    raw_url = url
                elif isinstance(url, dict):
                    raw_url = url.get("raw")
                if not raw_url:
                    continue
                path = _extract_path_from_url(str(raw_url))
                if not path:
                    continue

                endpoints.append(
                    Endpoint(
                        method=method,
                        path=path,
                        file_path=str(file_path),
                        line_number=0,
                        confidence=0.95,
                        source="spec",
                        function_name=it.get("name"),
                        metadata={"postman_file": str(file_path)},
                    )
                )

        walk_items(data.get("item") or [])
        return endpoints

    def _looks_like_insomnia(self, data: Dict[str, Any]) -> bool:
        if "__export_format" in data or "__export_date" in data:
            return True
        return isinstance(data.get("resources"), list)

    def _endpoints_from_insomnia_dict(self, data: Dict[str, Any], file_path: Path) -> List[Endpoint]:
        endpoints: List[Endpoint] = []
        resources = data.get("resources")
        if not isinstance(resources, list):
            return endpoints

        for r in resources:
            if not isinstance(r, dict):
                continue
            if r.get("_type") != "request":
                continue
            method = _httpmethod_from_string(r.get("method") or "") or HTTPMethod.GET
            url = r.get("url")
            if not isinstance(url, str):
                continue
            path = _extract_path_from_url(url)
            if not path:
                continue
            endpoints.append(
                Endpoint(
                    method=method,
                    path=path,
                    file_path=str(file_path),
                    line_number=0,
                    confidence=0.95,
                    source="spec",
                    function_name=r.get("name"),
                    metadata={"insomnia_file": str(file_path)},
                )
            )

        return endpoints


class ConfigRoutesExtractor:
    """Layer 2: Parse route config files."""

    def __init__(self) -> None:
        self.logger = get_logger(__name__)

    def extract(self, repo_path: Path) -> Tuple[List[Endpoint], int]:
        endpoints: List[Endpoint] = []
        parsed_files = 0

        for file_path in self._find_config_candidates(repo_path):
            try:
                new_eps = self._extract_from_config_file(file_path)
                if new_eps:
                    endpoints.extend(new_eps)
                parsed_files += 1
            except Exception as e:
                self.logger.debug("Config route parse failed", file=str(file_path), error=str(e))

        return endpoints, parsed_files

    def _find_config_candidates(self, repo_path: Path) -> Iterable[Path]:
        for p in _iter_repo_files(repo_path):
            name = p.name.lower()
            if name.endswith((".yml", ".yaml")) and "routes" in name:
                yield p
            if name == "routes.rb":
                yield p
            if name in {"application.yml", "application.yaml", "application.properties"}:
                yield p
            # Some gateways use dedicated files
            if "gateway" in name and name.endswith((".yml", ".yaml", ".properties")):
                yield p

    def _extract_from_config_file(self, file_path: Path) -> List[Endpoint]:
        name = file_path.name.lower()
        if name.endswith("routes.rb"):
            return self._extract_from_rails_routes(file_path)
        if name.endswith((".yml", ".yaml")) and "routes" in name:
            return self._extract_from_symfony_routes_yaml(file_path)
        if name.endswith((".yml", ".yaml", ".properties")) and ("application" in name or "gateway" in name):
            return self._extract_from_spring_gateway_config(file_path)
        return []

    def _extract_from_rails_routes(self, file_path: Path) -> List[Endpoint]:
        text = _safe_read_text(file_path, max_bytes=500_000) or ""
        endpoints: List[Endpoint] = []
        for line_num, line in enumerate(text.splitlines(), 1):
            m = re.search(r"\b(get|post|put|patch|delete)\s+['\"]([^'\"]+)['\"]", line)
            if m:
                method = _httpmethod_from_string(m.group(1)) or HTTPMethod.GET
                path = _normalize_candidate_path(m.group(2))
                if path:
                    endpoints.append(
                        Endpoint(
                            method=method,
                            path=path,
                            file_path=str(file_path),
                            line_number=line_num,
                            confidence=0.75,
                            source="config",
                            metadata={"pattern": "rails_routes_rb"},
                        )
                    )

            m2 = re.search(r"\bmatch\s+['\"]([^'\"]+)['\"].*?via:\s*\[([^\]]+)\]", line)
            if m2:
                path = _normalize_candidate_path(m2.group(1))
                if not path:
                    continue
                methods_raw = m2.group(2)
                methods = re.findall(r":(get|post|put|patch|delete|head|options)", methods_raw, flags=re.IGNORECASE)
                for mm in methods:
                    http_method = _httpmethod_from_string(mm)
                    if not http_method:
                        continue
                    endpoints.append(
                        Endpoint(
                            method=http_method,
                            path=path,
                            file_path=str(file_path),
                            line_number=line_num,
                            confidence=0.75,
                            source="config",
                            metadata={"pattern": "rails_match_via"},
                        )
                    )

        return endpoints

    def _extract_from_symfony_routes_yaml(self, file_path: Path) -> List[Endpoint]:
        raw = file_path.read_text(encoding="utf-8", errors="ignore")
        data = yaml.safe_load(raw) if raw.strip() else None
        endpoints: List[Endpoint] = []

        if not isinstance(data, dict):
            return endpoints

        def walk(node: Any, parent: Optional[Dict[str, Any]] = None) -> None:
            if isinstance(node, dict):
                # Route-like dict
                if isinstance(node.get("path"), str):
                    path = _normalize_candidate_path(node.get("path"))
                    if path:
                        methods = node.get("methods")
                        http_methods = self._methods_from_symfony(methods)
                        if not http_methods:
                            http_methods = [HTTPMethod.GET]
                        for hm in http_methods:
                            endpoints.append(
                                Endpoint(
                                    method=hm,
                                    path=path,
                                    file_path=str(file_path),
                                    line_number=0,
                                    confidence=0.80,
                                    source="config",
                                    metadata={"pattern": "symfony_routes_yaml"},
                                )
                            )
                for v in node.values():
                    walk(v, node)
            elif isinstance(node, list):
                for it in node:
                    walk(it, parent)

        walk(data)
        return endpoints

    def _methods_from_symfony(self, methods_node: Any) -> List[HTTPMethod]:
        if not methods_node:
            return []
        methods: List[str] = []
        if isinstance(methods_node, str):
            methods = [methods_node]
        elif isinstance(methods_node, list):
            methods = [str(m) for m in methods_node]

        out: List[HTTPMethod] = []
        for m in methods:
            hm = _httpmethod_from_string(m)
            if hm:
                out.append(hm)
        return out

    def _extract_from_spring_gateway_config(self, file_path: Path) -> List[Endpoint]:
        text = _safe_read_text(file_path, max_bytes=800_000) or ""
        endpoints: List[Endpoint] = []

        # Spring Cloud Gateway predicate examples:
        # - Path=/foo/**
        # - Method=GET
        # properties: spring.cloud.gateway.routes[0].predicates[0]=Path=/foo/**
        for line_num, line in enumerate(text.splitlines(), 1):
            pm = re.search(r"\bPath\s*=\s*([^\s,]+)", line)
            if pm:
                path = _normalize_candidate_path(pm.group(1))
                if not path:
                    continue
                endpoints.append(
                    Endpoint(
                        method=HTTPMethod.GET,
                        path=path,
                        file_path=str(file_path),
                        line_number=line_num,
                        confidence=0.70,
                        source="config",
                        metadata={"pattern": "spring_cloud_gateway_path_predicate"},
                    )
                )

        return endpoints


class UniversalFallbackExtractor:
    """Layer 4: Language-agnostic heuristics over arbitrary text."""

    def __init__(self) -> None:
        self.logger = get_logger(__name__)

    def extract(self, repo_path: Path) -> Tuple[List[Endpoint], int]:
        endpoints: List[Endpoint] = []
        scanned_files = 0

        method_path = re.compile(
            r"\b(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\b\s+(/[^\s\"']+)",
            flags=re.IGNORECASE,
        )
        key_path = re.compile(
            r"\b(?:path|url|endpoint|route)\b\s*[:=]\s*[\"'](/[^\"']+)[\"']",
            flags=re.IGNORECASE,
        )

        for file_path in _iter_repo_files(repo_path):
            text = _safe_read_text(file_path)
            if text is None:
                continue
            scanned_files += 1

            for m in method_path.finditer(text):
                hm = _httpmethod_from_string(m.group(1)) or HTTPMethod.GET
                path = _normalize_candidate_path(m.group(2))
                if not path:
                    continue
                line_number = text.count("\n", 0, m.start()) + 1
                endpoints.append(
                    Endpoint(
                        method=hm,
                        path=path,
                        file_path=str(file_path),
                        line_number=line_number,
                        confidence=0.25,
                        source="universal",
                        metadata={"pattern": "method_path"},
                    )
                )

            for m in key_path.finditer(text):
                path = _normalize_candidate_path(m.group(1))
                if not path:
                    continue
                line_number = text.count("\n", 0, m.start()) + 1
                endpoints.append(
                    Endpoint(
                        method=HTTPMethod.GET,
                        path=path,
                        file_path=str(file_path),
                        line_number=line_number,
                        confidence=0.20,
                        source="universal",
                        metadata={"pattern": "key_path"},
                    )
                )

        return endpoints, scanned_files
