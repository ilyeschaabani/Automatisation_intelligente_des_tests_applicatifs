"""OpenAPI 3.0 specification generator.

The pipeline can extract endpoints from multiple stacks. For Spring Boot
endpoints (AST-based), we often have access to the cloned repository sources.
When `repo_root` is provided, we opportunistically infer response schemas and
status codes from controller method return types/return statements.

We also promote repeated/complex schemas into `components.schemas` and replace
inline schemas with `$ref` to improve reuse and consistency.
"""

from __future__ import annotations

import os
import json
import re
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Set

from .endpoint import Endpoint, HTTPMethod

try:
    # Optional: only used when repo_root points to a Java codebase.
    from ..java_dto_extractor import JavaDTOExtractor, java_type_to_schema
except Exception:  # pragma: no cover
    JavaDTOExtractor = None  # type: ignore
    java_type_to_schema = None  # type: ignore


def _canonical_json(obj: Any) -> str:
    return json.dumps(obj, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


_SENSITIVE_FIELD_HINTS = {
    "password",
    "passwd",
    "pwd",
    "secret",
    "token",
    "refresh_token",
    "access_token",
    "api_key",
    "apikey",
    "client_secret",
    "private_key",
}


def _is_sensitive_field(name: Optional[str]) -> bool:
    if not name:
        return False
    n = re.sub(r"[^a-z0-9]+", "_", name.strip().lower()).strip("_")
    if not n:
        return False
    if n in _SENSITIVE_FIELD_HINTS:
        return True
    # Common patterns
    if n.endswith("password") or n.endswith("_token") or n.endswith("token"):
        return True
    if "password" in n or "secret" in n:
        return True
    if n in {"authorization", "auth_token"}:
        return True
    return False


def _looks_placeholder_example(example: Any) -> bool:
    if example is None:
        return True
    if example == {} or example == [] or example == "":
        return True
    if isinstance(example, str) and example.strip().lower() in {"string", "example", "n/a"}:
        return True

    # If the example is mostly "string" leaves, it is likely synthetic.
    leaves: List[Any] = []

    def walk(x: Any) -> None:
        if isinstance(x, dict):
            for v in x.values():
                walk(v)
        elif isinstance(x, list):
            for v in x:
                walk(v)
        else:
            leaves.append(x)

    walk(example)
    if not leaves:
        return True
    stringish = sum(1 for v in leaves if isinstance(v, str) and v.strip().lower() in {"string", ""})
    return (stringish / max(1, len(leaves))) >= 0.6


def _guess_string_example(field_name: Optional[str], schema: Dict[str, Any]) -> str:
    n = (field_name or "").strip()
    n_low = n.lower()

    fmt = schema.get("format")
    if fmt == "email" or "email" in n_low:
        return "user@example.com"
    if fmt == "uuid" or n_low.endswith("uuid"):
        return "123e4567-e89b-12d3-a456-426614174000"
    if fmt == "date":
        return "2026-01-01"
    if fmt == "date-time" or n_low.endswith("_at") or n_low.endswith("date"):
        return "2026-01-01T00:00:00Z"
    if fmt == "binary":
        return "<binary>"

    enum = schema.get("enum")
    if isinstance(enum, list) and enum:
        return str(enum[0])

    if _is_sensitive_field(n):
        return "***"

    if n_low in {"id", "_id"} or n_low.endswith("id"):
        return "1"
    if "title" in n_low:
        return "AI-based API Testing Platform"
    if "description" in n_low:
        return "Auto-generated description"
    if "username" in n_low:
        return "jdoe"
    if "phone" in n_low:
        return "+1234567890"
    if "url" in n_low:
        return "https://example.com"
    if "filepath" in n_low or n_low.endswith("_path"):
        return "/tmp/file"
    if "filename" in n_low or n_low.endswith("_file") or n_low.endswith("_name"):
        return "document.pdf"

    return "string"


def _schema_example(schema: Any, field_name: Optional[str] = None, depth: int = 0, max_depth: int = 4) -> Any:
    if depth > max_depth:
        return None
    if not isinstance(schema, dict):
        return None
    if "example" in schema:
        return schema.get("example")

    t = schema.get("type")
    if t == "string":
        return _guess_string_example(field_name, schema)
    if t == "integer":
        if (field_name or "").lower().endswith("id"):
            return 1
        if "score" in (field_name or "").lower():
            return 80
        return 1
    if t == "number":
        return 1.0
    if t == "boolean":
        return True
    if t == "array":
        ex = _schema_example(schema.get("items"), field_name=field_name, depth=depth + 1, max_depth=max_depth)
        return [] if ex is None else [ex]
    if t == "object":
        props = schema.get("properties")
        if isinstance(props, dict) and props:
            out: Dict[str, Any] = {}
            for k, v in props.items():
                if _is_sensitive_field(k):
                    out[k] = "***"
                else:
                    out[k] = _schema_example(v, field_name=k, depth=depth + 1, max_depth=max_depth)
            return out
        addl = schema.get("additionalProperties")
        if isinstance(addl, dict):
            return {"key": _schema_example(addl, field_name="value", depth=depth + 1, max_depth=max_depth)}
        return {}
    return None


class _SchemaRegistry:
    def __init__(self):
        self._schemas: Dict[str, Dict[str, Any]] = {}
        self._hash_to_name: Dict[str, str] = {}

    @property
    def schemas(self) -> Dict[str, Dict[str, Any]]:
        return self._schemas

    def ensure(self, name_hint: str, schema: Dict[str, Any]) -> str:
        h = _canonical_json(schema)
        existing = self._hash_to_name.get(h)
        if existing:
            return existing

        safe = re.sub(r"[^A-Za-z0-9_]+", "_", (name_hint or "Schema")).strip("_")
        if not safe:
            safe = "Schema"
        base = safe
        i = 2
        while safe in self._schemas and _canonical_json(self._schemas[safe]) != h:
            safe = f"{base}_{i}"
            i += 1

        self._schemas[safe] = schema
        self._hash_to_name[h] = safe
        return safe

    def ref_or_inline(self, name_hint: str, schema: Any) -> Any:
        if not isinstance(schema, dict):
            return schema
        t = schema.get("type")
        # Keep primitives inline.
        if t in {"string", "integer", "number", "boolean"}:
            return schema
        if t == "array" and isinstance(schema.get("items"), dict):
            out = dict(schema)
            out["items"] = self.ref_or_inline(f"{name_hint}Item", schema["items"])
            return out
        if t == "object":
            props = schema.get("properties")
            addl = schema.get("additionalProperties")
            # Avoid creating artificial components for "unknown object" responses.
            if not (isinstance(props, dict) and props) and not isinstance(addl, dict) and not schema.get("x-java-type"):
                return {"type": "object"}
            name = self.ensure(name_hint, schema)
            return {"$ref": f"#/components/schemas/{name}"}
        return schema


_PROBLEM_DETAILS_SCHEMA: Dict[str, Any] = {
    "type": "object",
    "properties": {
        "type": {"type": "string"},
        "title": {"type": "string"},
        "status": {"type": "integer", "format": "int32"},
        "detail": {"type": "string"},
        "instance": {"type": "string"},
    },
    "required": ["title", "status"],
    "example": {"title": "Bad Request", "status": 400, "detail": "Validation failed"},
}


def _unwrap_response_entity(java_type: str) -> str:
    s = (java_type or "").strip()
    m = re.match(r"ResponseEntity\s*<\s*(.+)\s*>\s*$", s)
    return m.group(1).strip() if m else s


def _strip_generics(java_type: str) -> str:
    s = (java_type or "").strip()
    return s.split("<", 1)[0].strip() if "<" in s else s


def _resolve_java_file(file_path: str, repo_root: Optional[Path]) -> Optional[Path]:
    if not file_path:
        return None

    direct = Path(file_path)
    if direct.exists():
        return direct

    if not repo_root:
        return None

    target_posix = str(file_path).replace("\\", "/")
    filename = Path(file_path).name
    if not filename:
        return None

    hits: List[Path] = []
    try:
        hits = list(Path(repo_root).rglob(filename))
    except Exception:
        hits = []
    if not hits:
        return None

    def score(p: Path) -> int:
        a = p.as_posix().split("/")
        b = target_posix.split("/")
        s = 0
        for i in range(1, min(len(a), len(b)) + 1):
            if a[-i].lower() == b[-i].lower():
                s += 1
            else:
                break
        return s

    hits.sort(key=score, reverse=True)
    return hits[0]


def _method_signature_matches(java_source: str, function_name: str) -> List[re.Match]:
    if not java_source or not function_name:
        return []
    # Capture return type (group 1). We keep this permissive to support generics.
    sig_re = re.compile(
        rf"(?:^|\n)\s*(?:public|protected|private)?\s*(?:static\s+)?(?:final\s+)?([^\n\(\{{;]]+?)\s+{re.escape(function_name)}\s*\(",
        re.M,
    )
    return list(sig_re.finditer(java_source))


def _infer_java_method_return_type(java_source: str, function_name: str, line_number: Optional[int] = None) -> Optional[str]:
    matches = _method_signature_matches(java_source, function_name)
    if not matches:
        return None
    if line_number is None or line_number <= 0:
        rt = matches[0].group(1)
        return re.sub(r"\s+", " ", rt).strip()

    # Choose the signature match closest to the endpoint line_number.
    best: Optional[re.Match] = None
    best_dist = 10**9
    for m in matches:
        sig_line = java_source[: m.start()].count("\n") + 1
        dist = abs(sig_line - line_number)
        if dist < best_dist:
            best_dist = dist
            best = m
    if not best:
        return None
    rt = best.group(1)
    return re.sub(r"\s+", " ", rt).strip()


def _extract_method_block(java_source: str, function_name: str, line_number: Optional[int] = None) -> Optional[str]:
    """Extract the Java method block (signature + body) for better-localized inference."""
    matches = _method_signature_matches(java_source, function_name)
    if not matches:
        return None
    chosen = matches[0]
    if line_number is not None and line_number > 0 and len(matches) > 1:
        chosen = min(matches, key=lambda m: abs((java_source[: m.start()].count("\n") + 1) - line_number))

    start = chosen.start()
    # Find first '{' after signature
    open_brace = java_source.find("{", chosen.end())
    semi = java_source.find(";", chosen.end())
    if open_brace == -1 or (semi != -1 and semi < open_brace):
        # abstract/interface method
        end = semi if semi != -1 else min(len(java_source), chosen.end() + 200)
        return java_source[start:end]

    depth = 0
    i = open_brace
    while i < len(java_source):
        ch = java_source[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return java_source[start : i + 1]
        i += 1
    return java_source[start:]


def _infer_status_codes(java_source: str, function_name: str, line_number: Optional[int] = None) -> Set[int]:
    if not java_source or not function_name:
        return set()

    window = _extract_method_block(java_source, function_name, line_number=line_number) or ""
    if not window:
        return set()

    codes_map = {
        "OK": 200,
        "CREATED": 201,
        "NO_CONTENT": 204,
        "BAD_REQUEST": 400,
        "UNAUTHORIZED": 401,
        "FORBIDDEN": 403,
        "NOT_FOUND": 404,
        "INTERNAL_SERVER_ERROR": 500,
    }

    out: Set[int] = set()
    m = re.search(r"@ResponseStatus\s*\(\s*HttpStatus\.([A-Z_]+)\s*\)", window)
    if m and m.group(1) in codes_map:
        out.add(codes_map[m.group(1)])

    for m in re.finditer(r"HttpStatus\.([A-Z_]+)", window):
        if m.group(1) in codes_map:
            out.add(codes_map[m.group(1)])

    # Common ResponseEntity shortcuts
    if re.search(r"ResponseEntity\.ok\s*\(", window):
        out.add(200)
    if re.search(r"ResponseEntity\.created\s*\(", window):
        out.add(201)
    if re.search(r"ResponseEntity\.noContent\s*\(", window):
        out.add(204)
    if re.search(r"ResponseEntity\.notFound\s*\(", window):
        out.add(404)
    if re.search(r"ResponseEntity\.badRequest\s*\(", window):
        out.add(400)
    if re.search(r"ResponseEntity\.status\s*\(\s*HttpStatus\.([A-Z_]+)\s*\)", window):
        # HttpStatus.* already captured above.
        pass
    return out


def _infer_map_put_properties(java_source: str, function_name: str) -> Optional[Dict[str, Any]]:
    if not java_source or not function_name:
        return None
    window = _extract_method_block(java_source, function_name) or java_source
    props: Dict[str, Any] = {}
    for m in re.finditer(r"\.put\(\s*\"([^\"]+)\"\s*,\s*([^\)]+)\)", window):
        key = m.group(1)
        val = m.group(2).strip()
        if val.startswith('"'):
            props[key] = {"type": "string"}
        elif re.fullmatch(r"\d+", val):
            props[key] = {"type": "integer", "format": "int32"}
        elif re.fullmatch(r"\d+\.\d+", val):
            props[key] = {"type": "number"}
        elif val in {"true", "false"}:
            props[key] = {"type": "boolean"}
        else:
            props[key] = {"type": "string"}
    return props or None


def _apply_enum_schema_hints(schema: Any, enum_index: Dict[str, List[str]]) -> Any:
    if not enum_index:
        return schema
    if isinstance(schema, list):
        return [_apply_enum_schema_hints(x, enum_index) for x in schema]
    if not isinstance(schema, dict):
        return schema

    # If the schema declares a java type that is an enum, always map as string enum.
    if isinstance(schema.get("x-java-type"), str):
        base = schema["x-java-type"].split(".")[-1].split("<", 1)[0].strip()
        if base in enum_index:
            return {"type": "string", "enum": enum_index[base], "x-java-type": schema["x-java-type"]}

    if schema.get("type") == "string" and isinstance(schema.get("x-java-type"), str):
        base = schema["x-java-type"].split(".")[-1].split("<", 1)[0].strip()
        if base in enum_index:
            out = dict(schema)
            out["enum"] = enum_index[base]
            return out

    if schema.get("type") == "array" and isinstance(schema.get("items"), dict):
        out = dict(schema)
        out["items"] = _apply_enum_schema_hints(schema["items"], enum_index)
        return out
    if schema.get("type") == "object" and isinstance(schema.get("properties"), dict):
        out = dict(schema)
        out["properties"] = {k: _apply_enum_schema_hints(v, enum_index) for k, v in schema["properties"].items()}
        return out
    if schema.get("type") == "object" and isinstance(schema.get("additionalProperties"), dict):
        out = dict(schema)
        out["additionalProperties"] = _apply_enum_schema_hints(schema["additionalProperties"], enum_index)
        return out
    return schema


def _expand_x_java_type_refs(schema: Any, dto_extractor: Any, depth: int = 3, visiting: Optional[Set[str]] = None) -> Any:
    if depth <= 0 or dto_extractor is None:
        return schema
    if visiting is None:
        visiting = set()

    if isinstance(schema, list):
        return [_expand_x_java_type_refs(x, dto_extractor, depth=depth, visiting=visiting) for x in schema]

    if not isinstance(schema, dict):
        return schema

    if schema.get("type") == "array" and isinstance(schema.get("items"), dict):
        out = dict(schema)
        out["items"] = _expand_x_java_type_refs(schema["items"], dto_extractor, depth=depth, visiting=visiting)
        return out

    if schema.get("type") == "object" and isinstance(schema.get("additionalProperties"), dict):
        out = dict(schema)
        out["additionalProperties"] = _expand_x_java_type_refs(
            schema["additionalProperties"], dto_extractor, depth=depth, visiting=visiting
        )
        return out

    if schema.get("type") == "object" and isinstance(schema.get("x-java-type"), str):
        xjt = schema.get("x-java-type")
        props = schema.get("properties")
        if not (isinstance(props, dict) and props):
            base = str(xjt).split(".")[-1].split("<", 1)[0].strip() if xjt else ""
            if base and base not in visiting:
                visiting.add(base)
                resolved = dto_extractor.extract_schema(base, expand_refs_depth=0)
                visiting.remove(base)
                if isinstance(resolved, dict) and isinstance(resolved.get("properties"), dict) and resolved["properties"]:
                    merged = {**resolved, "x-java-type": xjt}
                    return _expand_x_java_type_refs(merged, dto_extractor, depth=depth - 1, visiting=visiting)

    if schema.get("type") == "object" and isinstance(schema.get("properties"), dict):
        out = dict(schema)
        out["properties"] = {
            k: _expand_x_java_type_refs(v, dto_extractor, depth=depth, visiting=visiting)
            for k, v in schema["properties"].items()
        }
        return out

    return schema


def _filter_sensitive_schema(schema: Any, *, drop_fields: bool) -> Any:
    """Filter sensitive fields out of object schemas.

    - If drop_fields=True, sensitive properties are removed from the schema.
    - Examples are not handled here (they are handled by `_schema_example`).
    """
    if isinstance(schema, list):
        return [_filter_sensitive_schema(x, drop_fields=drop_fields) for x in schema]
    if not isinstance(schema, dict):
        return schema

    if schema.get("type") == "array" and isinstance(schema.get("items"), dict):
        out = dict(schema)
        out["items"] = _filter_sensitive_schema(schema["items"], drop_fields=drop_fields)
        return out

    if schema.get("type") == "object":
        out = dict(schema)
        props = out.get("properties")
        if isinstance(props, dict) and props:
            new_props: Dict[str, Any] = {}
            for k, v in props.items():
                if drop_fields and _is_sensitive_field(k):
                    continue
                new_props[k] = _filter_sensitive_schema(v, drop_fields=drop_fields)
            out["properties"] = new_props

            req = out.get("required")
            if isinstance(req, list) and req:
                out["required"] = [r for r in req if not (drop_fields and _is_sensitive_field(str(r)))]

        addl = out.get("additionalProperties")
        if isinstance(addl, dict):
            out["additionalProperties"] = _filter_sensitive_schema(addl, drop_fields=drop_fields)

        return out

    # For primitives, keep as-is.
    return schema


def _name_hint_from_schema(schema: Any, fallback: str) -> str:
    if not isinstance(schema, dict):
        return _safe_schema_name(fallback)
    if schema.get("type") == "object" and isinstance(schema.get("x-java-type"), str):
        base = schema["x-java-type"].split(".")[-1].split("<", 1)[0].strip()
        if base:
            return _safe_schema_name(base)
    if schema.get("type") == "array" and isinstance(schema.get("items"), dict):
        items = schema["items"]
        if isinstance(items.get("x-java-type"), str):
            base = items["x-java-type"].split(".")[-1].split("<", 1)[0].strip()
            if base:
                return _safe_schema_name(f"{base}List")
    return _safe_schema_name(fallback)


def _safe_schema_name(name: str) -> str:
    s = re.sub(r"[^A-Za-z0-9_]+", "_", (name or "Schema")).strip("_")
    return s or "Schema"


@dataclass
class OpenAPISpec:
    """OpenAPI 3.0 specification builder"""

    title: str = "API Specification"
    version: str = "1.0.0"
    description: str = ""
    # Safe default: keep requests local when no base_url is known.
    servers: List[Dict[str, str]] = field(default_factory=lambda: [{"url": "http://localhost"}])
    endpoints: List[Endpoint] = field(default_factory=list)
    repo_root: Optional[Path] = None

    def to_dict(self) -> Dict[str, Any]:
        """Convert to OpenAPI 3.0 JSON dictionary"""
        registry = _SchemaRegistry()
        registry.ensure("ProblemDetails", dict(_PROBLEM_DETAILS_SCHEMA))

        dto_extractor = None
        enum_index: Dict[str, List[str]] = {}
        if self.repo_root and JavaDTOExtractor is not None:
            try:
                java_files = list(Path(self.repo_root).rglob("*.java"))
                dto_extractor = JavaDTOExtractor.from_java_files(Path(self.repo_root), java_files)

                enum_re = re.compile(r"\benum\s+(\w+)\s*\{([^}]+)\}", re.S)
                for jf in java_files:
                    try:
                        txt = jf.read_text(encoding="utf-8", errors="ignore")
                    except Exception:
                        continue
                    for m in enum_re.finditer(txt):
                        name = m.group(1)
                        body = m.group(2)
                        # Enum values appear before the optional ';' section.
                        if ";" in body:
                            body = body.split(";", 1)[0]
                        vals = []
                        for v in body.split(","):
                            vv = v.strip()
                            if not vv:
                                continue
                            vv = re.sub(r"\s+", " ", vv)
                            # Strip possible constructor args: VALUE(...)
                            vv = vv.split("(", 1)[0].strip()
                            if re.match(r"^[A-Z][A-Z0-9_]*$", vv):
                                vals.append(vv)
                        if vals:
                            enum_index.setdefault(name, vals)
            except Exception:
                dto_extractor = None

        strict_confidence = float(os.getenv("OPENAPI_STRICT_CONFIDENCE", "0.7"))
        filter_sensitive_requests = os.getenv("OPENAPI_FILTER_SENSITIVE_REQUESTS", "false").strip().lower() in {
            "1",
            "true",
            "yes",
            "y",
            "on",
        }
        filter_sensitive_responses = os.getenv("OPENAPI_FILTER_SENSITIVE_RESPONSES", "true").strip().lower() in {
            "1",
            "true",
            "yes",
            "y",
            "on",
        }

        spec: Dict[str, Any] = {
            "openapi": "3.0.3",
            "info": {
                "title": self.title,
                "version": self.version,
                "description": self.description,
                "x-generated": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
                "x-endpoint-count": len(self.endpoints),
            },
            "servers": self.servers,
            "paths": {},
            "components": {
                "securitySchemes": {
                    "bearerAuth": {
                        "type": "http",
                        "scheme": "bearer",
                        "bearerFormat": "JWT",
                    }
                },
                "schemas": registry.schemas,
            },
            "security": [{"bearerAuth": []}],
            "tags": [],
        }

        # Group endpoints by normalized full path
        path_groups: Dict[str, Dict[HTTPMethod, Endpoint]] = {}
        for endpoint in self.endpoints:
            normalized_path = self._normalize_path_to_openapi(endpoint.full_path or endpoint.path)
            if normalized_path not in path_groups:
                path_groups[normalized_path] = {}
            path_groups[normalized_path][endpoint.method] = endpoint

        for path, methods_dict in path_groups.items():
            spec["paths"][path] = {}
            for method, endpoint in methods_dict.items():
                operation: Dict[str, Any] = {
                    "summary": endpoint.function_name or f"{method.upper()} {path}",
                    "tags": self._extract_tags(endpoint),
                }

                request_meta = None
                if endpoint.metadata and isinstance(endpoint.metadata, dict):
                    request_meta = endpoint.metadata.get("request")

                if isinstance(request_meta, dict):
                    params = self._build_parameters_from_request_meta(request_meta)
                    if params and enum_index:
                        for p in params:
                            if isinstance(p, dict) and isinstance(p.get("schema"), dict):
                                p["schema"] = _apply_enum_schema_hints(p["schema"], enum_index)
                    if params:
                        operation["parameters"] = self._reconcile_parameters_with_path(params, path)

                    request_body = self._build_request_body_from_request_meta(request_meta)
                    if request_body:
                        for ctype, media in (request_body.get("content") or {}).items():
                            if not isinstance(media, dict):
                                continue
                            schema = media.get("schema")
                            if isinstance(schema, dict):
                                schema = _apply_enum_schema_hints(schema, enum_index)
                                schema = _expand_x_java_type_refs(schema, dto_extractor, depth=3)

                                # Optional filtering in request schemas.
                                if filter_sensitive_requests:
                                    schema = _filter_sensitive_schema(schema, drop_fields=True)

                                dto_type = None
                                body_meta = request_meta.get("body") if isinstance(request_meta, dict) else None
                                if isinstance(body_meta, dict):
                                    dto_type = body_meta.get("dto_type")
                                base_hint = (dto_type or endpoint.function_name or "Request").split(".")[-1]
                                hint = _name_hint_from_schema(schema, f"{base_hint}Request")
                                media["schema"] = registry.ref_or_inline(hint, schema)

                                # Improve examples: replace placeholders with heuristic example.
                                if _looks_placeholder_example(media.get("example")):
                                    ex = _schema_example(schema)
                                    if ex is not None:
                                        media["example"] = ex
                        operation["requestBody"] = request_body

                elif endpoint.parameters:
                    operation["parameters"] = self._build_parameters(endpoint.parameters, path)

                operation["responses"] = self._build_responses(
                    endpoint,
                    dto_extractor,
                    enum_index,
                    registry,
                    strict_confidence=strict_confidence,
                    filter_sensitive_responses=filter_sensitive_responses,
                )

                if endpoint.metadata:
                    operation["x-metadata"] = endpoint.metadata
                if endpoint.confidence < 1.0:
                    operation["x-confidence"] = endpoint.confidence
                operation["x-source"] = endpoint.source

                spec["paths"][path][method.value] = operation

        # Unique tags
        all_tags: Set[str] = set()
        for endpoint in self.endpoints:
            all_tags.update(self._extract_tags(endpoint))
        spec["tags"] = [{"name": tag} for tag in sorted(all_tags)]

        return spec

    def _build_responses(
        self,
        endpoint: Endpoint,
        dto_extractor: Any,
        enum_index: Dict[str, List[str]],
        registry: _SchemaRegistry,
        *,
        strict_confidence: float,
        filter_sensitive_responses: bool,
    ) -> Dict[str, Any]:
        inferred_codes: Set[int] = set()
        success_code = 200
        success_content_type = "application/json"
        success_schema: Dict[str, Any] = {"type": "object"}

        # If confidence is low, allow a generic response without attempting strict inference.
        allow_generic = endpoint.confidence < strict_confidence

        if self.repo_root and endpoint.file_path and endpoint.function_name and not allow_generic:
            try:
                p = _resolve_java_file(endpoint.file_path, self.repo_root)
                if p and p.exists():
                    src = p.read_text(encoding="utf-8", errors="ignore")
                    inferred_codes = _infer_status_codes(src, endpoint.function_name, line_number=endpoint.line_number)
                    rt = _infer_java_method_return_type(src, endpoint.function_name, line_number=endpoint.line_number)
                    if rt:
                        payload = _unwrap_response_entity(rt).strip()

                        # Optional<T> -> T
                        m_opt = re.match(r"Optional\s*<\s*(.+)\s*>", payload)
                        if m_opt:
                            payload = m_opt.group(1).strip()

                        if payload in {"Void", "void"}:
                            success_code = 204
                            return {"204": {"description": "No Content"}}

                        if _strip_generics(payload) in {"Resource", "InputStreamResource"} or payload in {"byte[]"}:
                            success_content_type = "application/octet-stream"
                            success_schema = {"type": "string", "format": "binary"}
                        elif payload == "?":
                            props = _infer_map_put_properties(src, endpoint.function_name)
                            if props:
                                success_schema = {
                                    "type": "object",
                                    "properties": props,
                                    "required": sorted(props.keys()),
                                }
                        else:
                            if java_type_to_schema is not None:
                                mapped = java_type_to_schema(payload)
                                mapped = _apply_enum_schema_hints(mapped, enum_index)

                                xjt = mapped.get("x-java-type") if isinstance(mapped, dict) else None
                                base = None
                                if isinstance(xjt, str):
                                    base = xjt.split(".")[-1].split("<", 1)[0].strip()
                                if base and dto_extractor is not None:
                                    extracted = dto_extractor.extract_schema(base, expand_refs_depth=3)
                                    if isinstance(extracted, dict) and isinstance(extracted.get("properties"), dict) and extracted["properties"]:
                                        mapped = {**extracted, "x-java-type": xjt}
                                        mapped = _apply_enum_schema_hints(mapped, enum_index)
                                success_schema = mapped

                    if 201 in inferred_codes:
                        success_code = 201
                    elif 204 in inferred_codes:
                        success_code = 204
                    else:
                        success_code = 200
            except Exception:
                pass

        schema_obj = _apply_enum_schema_hints(success_schema, enum_index)
        schema_obj = _expand_x_java_type_refs(schema_obj, dto_extractor, depth=3)

        if filter_sensitive_responses:
            schema_obj = _filter_sensitive_schema(schema_obj, drop_fields=True)

        schema_ref = registry.ref_or_inline(
            _name_hint_from_schema(schema_obj, f"{endpoint.function_name or 'Response'}Response"),
            schema_obj,
        )
        success_example = _schema_example(schema_obj)

        responses: Dict[str, Any] = {
            str(success_code): {
                "description": "Success" if success_code != 204 else "No Content",
            }
        }
        if success_code != 204:
            responses[str(success_code)]["content"] = {
                success_content_type: {
                    "schema": schema_ref,
                }
            }
            if success_example is not None:
                responses[str(success_code)]["content"][success_content_type]["example"] = success_example

        # Only include error codes we actually detected in the method block.
        problem_ref = {"$ref": "#/components/schemas/ProblemDetails"}
        if 400 in inferred_codes:
            responses["400"] = {
                "description": "Bad Request",
                "content": {
                    "application/json": {
                        "schema": problem_ref,
                        "example": _PROBLEM_DETAILS_SCHEMA.get("example"),
                    }
                },
            }
        if 404 in inferred_codes:
            responses["404"] = {
                "description": "Not Found",
                "content": {
                    "application/json": {
                        "schema": problem_ref,
                        "example": {"title": "Not Found", "status": 404, "detail": "Resource not found"},
                    }
                },
            }
        if 500 in inferred_codes:
            responses["500"] = {
                "description": "Internal Server Error",
                "content": {
                    "application/json": {
                        "schema": problem_ref,
                        "example": {"title": "Internal Server Error", "status": 500, "detail": "Unexpected error"},
                    }
                },
            }

        return responses

    def _normalize_path_to_openapi(self, path: str) -> str:
        if not path:
            return "/"
        path = re.sub(r":(\w+)", r"{\1}", path)
        path = re.sub(r"\*(\w+)", r"{\1}", path)
        return path

    def _extract_tags(self, endpoint: Endpoint) -> List[str]:
        import os

        tags: List[str] = []

        if endpoint.file_path:
            filename = os.path.basename(endpoint.file_path)
            name_without_ext = os.path.splitext(filename)[0]
            for suffix in ["_controller", "controller", "_routes", "routes", "_service", "service"]:
                if name_without_ext.lower().endswith(suffix):
                    name_without_ext = name_without_ext[: -len(suffix)]
            if name_without_ext:
                name_tag = re.sub(r"([A-Z])", r"_\1", name_without_ext).lower().strip("_")
                if name_tag:
                    tags.append(name_tag)

        if endpoint.function_name:
            words = re.findall(r"[A-Z][a-z]*|[a-z]+", endpoint.function_name)
            if words:
                tags.append(words[0].lower())

        if not tags:
            tags.append("api")

        return list(set(tags))[:3]

    def _build_parameters(self, parameters: List[str], path: str = None) -> List[Dict[str, Any]]:
        param_objects: List[Dict[str, Any]] = []
        if path:
            path_params = re.findall(r"\{(\w+)\}", path)
            for param in path_params:
                param_objects.append(
                    {
                        "name": param,
                        "in": "path",
                        "required": True,
                        "schema": {"type": "string"},
                    }
                )
        else:
            for param in parameters:
                if ":" in param:
                    name, param_type = param.split(":", 1)
                else:
                    name, param_type = param, "string"
                param_objects.append(
                    {
                        "name": name,
                        "in": "path",
                        "required": True,
                        "schema": {"type": self._map_type(param_type)},
                    }
                )
        return param_objects

    def _build_parameters_from_request_meta(self, request_meta: Dict[str, Any]) -> List[Dict[str, Any]]:
        out: List[Dict[str, Any]] = []

        def add_many(items: Any, location: str, force_required: Optional[bool] = None) -> None:
            if not isinstance(items, list):
                return
            for it in items:
                if not isinstance(it, dict):
                    continue
                name = it.get("name")
                if not name:
                    continue

                required = bool(it.get("required", True))
                if force_required is not None:
                    required = force_required

                schema = it.get("schema")
                if not isinstance(schema, dict):
                    schema = {"type": "string"}

                param_obj: Dict[str, Any] = {
                    "name": name,
                    "in": location,
                    "required": required,
                    "schema": schema,
                }
                if "example" in it:
                    param_obj["example"] = it.get("example")
                out.append(param_obj)

        add_many(request_meta.get("path"), "path", force_required=True)
        add_many(request_meta.get("query"), "query")
        add_many(request_meta.get("header"), "header")

        seen = set()
        uniq: List[Dict[str, Any]] = []
        for p in out:
            key = (p.get("in"), p.get("name"))
            if key in seen:
                continue
            seen.add(key)
            uniq.append(p)
        return uniq

    def _reconcile_parameters_with_path(self, parameters: List[Dict[str, Any]], path: str) -> List[Dict[str, Any]]:
        if not path or not isinstance(parameters, list):
            return parameters

        placeholders = [p for p in re.findall(r"\{([^{}]+)\}", path) if p]
        if not placeholders:
            return parameters

        placeholder_set = set(placeholders)
        kept: List[Dict[str, Any]] = []
        extraneous_path_params: List[Dict[str, Any]] = []

        for p in parameters:
            if not isinstance(p, dict):
                continue
            loc = p.get("in")
            name = p.get("name")
            if loc != "path":
                kept.append(p)
                continue
            if isinstance(name, str) and name in placeholder_set:
                kept.append(p)
            else:
                extraneous_path_params.append(p)

        existing_path_names = {
            p.get("name")
            for p in kept
            if isinstance(p, dict) and p.get("in") == "path" and isinstance(p.get("name"), str)
        }

        # If the only issue is a simple name mismatch, rename it.
        if len(placeholders) == 1 and placeholders[0] not in existing_path_names and len(extraneous_path_params) == 1:
            renamed = dict(extraneous_path_params[0])
            renamed["name"] = placeholders[0]
            renamed["in"] = "path"
            renamed["required"] = True
            kept.append(renamed)
            existing_path_names.add(placeholders[0])

        # Ensure every placeholder has a corresponding path parameter.
        for ph in placeholders:
            if ph in existing_path_names:
                continue
            kept.append(
                {
                    "name": ph,
                    "in": "path",
                    "required": True,
                    "schema": {"type": "string"},
                }
            )
            existing_path_names.add(ph)

        # De-dupe after reconciliation.
        seen = set()
        uniq: List[Dict[str, Any]] = []
        for p in kept:
            key = (p.get("in"), p.get("name"))
            if key in seen:
                continue
            seen.add(key)
            uniq.append(p)
        return uniq

    def _build_request_body_from_request_meta(self, request_meta: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        body = request_meta.get("body")
        if not isinstance(body, dict):
            return None

        content_type = body.get("content_type") or "application/json"
        schema = body.get("schema")
        if not isinstance(schema, dict):
            schema = {"type": "object"}

        required = bool(body.get("required", True))

        media_obj: Dict[str, Any] = {"schema": schema}
        if "example" in body:
            media_obj["example"] = body.get("example")

        return {
            "required": required,
            "content": {str(content_type): media_obj},
        }

    def _map_type(self, param_type: str) -> str:
        type_mapping = {
            "int": "integer",
            "integer": "integer",
            "string": "string",
            "bool": "boolean",
            "boolean": "boolean",
            "number": "number",
            "float": "number",
            "uuid": "string",
            "id": "string",
        }
        return type_mapping.get((param_type or "").lower(), "string")

    def to_json(self, indent: int = 2) -> str:
        return json.dumps(self.to_dict(), indent=indent, ensure_ascii=False)

    def save(self, filepath: str) -> None:
        Path(filepath).write_text(self.to_json(), encoding="utf-8")

    @classmethod
    def from_endpoints(
        cls,
        endpoints: List[Endpoint],
        title: str = "API Specification",
        version: str = "1.0.0",
        description: str = "",
    ) -> "OpenAPISpec":
        spec = cls(title=title, version=version, description=description)
        spec.endpoints = endpoints
        return spec

    @classmethod
    def from_endpoints_with_repo(
        cls,
        endpoints: List[Endpoint],
        repo_root: Path,
        title: str = "API Specification",
        version: str = "1.0.0",
        description: str = "",
    ) -> "OpenAPISpec":
        spec = cls(title=title, version=version, description=description)
        spec.endpoints = endpoints
        spec.repo_root = Path(repo_root) if repo_root else None
        return spec
