from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Set, Tuple


def _strip_java_generics(java_type: str) -> str:
    # Turn `List<String>` -> `List`, `Map<String, Integer>` -> `Map`
    s = java_type.strip()
    if "<" in s:
        return s[: s.index("<")].strip()
    return s


def _inner_generic_types(java_type: str) -> List[str]:
    # Very small generic parser: returns top-level generic args.
    # `List<String>` -> [`String`]
    # `Map<String, Integer>` -> [`String`, `Integer`]
    s = java_type.strip()
    if "<" not in s or ">" not in s:
        return []
    inner = s[s.index("<") + 1 : s.rindex(">")]

    out: List[str] = []
    cur: List[str] = []
    depth = 0
    for ch in inner:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth = max(0, depth - 1)
        elif ch == "," and depth == 0:
            part = "".join(cur).strip()
            if part:
                out.append(part)
            cur = []
            continue
        cur.append(ch)

    part = "".join(cur).strip()
    if part:
        out.append(part)
    return out


def java_type_to_schema(java_type: str) -> Dict:
    """Map a Java type string to a JSON-Schema/OpenAPI schema fragment."""
    t = java_type.strip()
    t_base = _strip_java_generics(t)

    # Arrays
    if t.endswith("[]"):
        item_t = t[:-2].strip()
        return {"type": "array", "items": java_type_to_schema(item_t)}

    # Common scalars
    scalar_map = {
        "String": {"type": "string"},
        "CharSequence": {"type": "string"},
        "UUID": {"type": "string", "format": "uuid"},
        "Boolean": {"type": "boolean"},
        "boolean": {"type": "boolean"},
        "Integer": {"type": "integer", "format": "int32"},
        "int": {"type": "integer", "format": "int32"},
        "Long": {"type": "integer", "format": "int64"},
        "long": {"type": "integer", "format": "int64"},
        "Float": {"type": "number", "format": "float"},
        "float": {"type": "number", "format": "float"},
        "Double": {"type": "number", "format": "double"},
        "double": {"type": "number", "format": "double"},
        "BigDecimal": {"type": "number"},
        "Date": {"type": "string", "format": "date-time"},
        "LocalDate": {"type": "string", "format": "date"},
        "LocalDateTime": {"type": "string", "format": "date-time"},
        "Instant": {"type": "string", "format": "date-time"},
    }
    if t_base in scalar_map:
        return dict(scalar_map[t_base])

    # Collections
    if t_base in {"List", "Set", "Collection", "Iterable"}:
        args = _inner_generic_types(t)
        item_schema = java_type_to_schema(args[0]) if args else {"type": "string"}
        return {"type": "array", "items": item_schema}

    if t_base in {"Map", "HashMap", "LinkedHashMap"}:
        args = _inner_generic_types(t)
        value_schema = java_type_to_schema(args[1]) if len(args) >= 2 else {"type": "string"}
        return {"type": "object", "additionalProperties": value_schema}

    # Fallback: refer to object
    return {"type": "object", "x-java-type": t}


_REQUIRED_ANNOTATIONS = {
    "NotNull",
    "NotBlank",
    "NotEmpty",
}


@dataclass
class _FieldInfo:
    name: str
    java_type: str
    required: bool = False
    constraints: Dict = None


_JAVA_STATEMENT_KEYWORDS = {
    "return",
    "if",
    "for",
    "while",
    "switch",
    "case",
    "break",
    "continue",
    "throw",
    "try",
    "catch",
    "finally",
    "new",
}


def _parse_constraints(annotations: List[str]) -> Tuple[bool, Dict[str, Any]]:
    is_required = False
    constraints: Dict[str, Any] = {}

    for ann in annotations:
        m = re.search(r"@([A-Za-z_][A-Za-z0-9_]*)", ann)
        if m and m.group(1) in _REQUIRED_ANNOTATIONS:
            is_required = True

        if "@Email" in ann:
            constraints["format"] = "email"

        m = re.search(r"@Size\s*\(\s*min\s*=\s*(\d+)", ann)
        if m:
            constraints["minLength"] = int(m.group(1))
        m = re.search(r"@Size\s*\(.*?max\s*=\s*(\d+)", ann)
        if m:
            constraints["maxLength"] = int(m.group(1))

        m = re.search(r"@Min\s*\(\s*(\d+)\s*\)", ann)
        if m:
            constraints["minimum"] = int(m.group(1))
        m = re.search(r"@Max\s*\(\s*(\d+)\s*\)", ann)
        if m:
            constraints["maximum"] = int(m.group(1))

    return is_required, constraints


def _split_top_level_commas(s: str) -> List[str]:
    out: List[str] = []
    cur: List[str] = []
    depth_angle = 0
    depth_paren = 0

    for ch in s:
        if ch == "<":
            depth_angle += 1
        elif ch == ">":
            depth_angle = max(0, depth_angle - 1)
        elif ch == "(":
            depth_paren += 1
        elif ch == ")":
            depth_paren = max(0, depth_paren - 1)
        elif ch == "," and depth_angle == 0 and depth_paren == 0:
            part = "".join(cur).strip()
            if part:
                out.append(part)
            cur = []
            continue
        cur.append(ch)

    part = "".join(cur).strip()
    if part:
        out.append(part)
    return out


def _find_class_or_record_decl(source: str, class_name: str) -> Optional[re.Match]:
    # Allow modifiers/annotations before the decl.
    # We intentionally keep this permissive.
    decl_re = re.compile(rf"\b(class|record)\s+{re.escape(class_name)}\b")
    return decl_re.search(source)


def _extract_brace_block(source: str, open_brace_index: int) -> str:
    depth = 0
    start = open_brace_index
    i = open_brace_index
    while i < len(source):
        ch = source[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return source[start : i + 1]
        i += 1
    return source[start:]


def _extract_record_components(source: str, decl_match: re.Match) -> Optional[str]:
    # Find the opening '(' after `record Name`.
    i = decl_match.end()
    while i < len(source) and source[i] not in "(\n":
        i += 1
    if i >= len(source) or source[i] != "(":
        return None
    start = i
    depth = 0
    while i < len(source):
        ch = source[i]
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return source[start + 1 : i]
        i += 1
    return None


def extract_schema_from_java_source(source: str, class_name: Optional[str] = None) -> Dict[str, Any]:
    """Heuristic DTO schema extraction from Java source.

    The extraction is intentionally conservative: it only considers fields declared
    at class scope (brace depth 1) or record components.

    Args:
        source: Full Java file contents
        class_name: Optional class/record name to target. If omitted, uses the first
            class/record declaration found.
    """
    properties: Dict[str, Dict[str, Any]] = {}
    required: List[str] = []

    # Pick a target class/record
    if class_name:
        decl = _find_class_or_record_decl(source, class_name)
    else:
        decl = re.search(r"\b(class|record)\s+(\w+)\b", source)
        class_name = decl.group(2) if decl else None

    if not decl or not class_name:
        return {"type": "object", "properties": {}}

    kind = decl.group(1)
    ann_buf: List[str] = []

    # Record: parse components from header
    if kind == "record":
        comps = _extract_record_components(source, decl)
        if comps:
            for part in _split_top_level_commas(comps):
                # Allow annotations on components; keep only the last `Type name`.
                cleaned = re.sub(r"@[A-Za-z_][A-Za-z0-9_]*(\([^)]*\))?", "", part).strip()
                m = re.match(r"^([\w\.$<>?,\[\]\s]+?)\s+(\w+)\s*$", cleaned)
                if not m:
                    continue
                java_type = m.group(1).strip()
                name = m.group(2).strip()
                if java_type.lower() in _JAVA_STATEMENT_KEYWORDS:
                    continue
                properties[name] = java_type_to_schema(java_type)
        out: Dict[str, Any] = {"type": "object", "properties": properties}
        if required:
            out["required"] = sorted(set(required))
        return out

    # Class: extract the class body and parse only depth==1 fields
    open_brace = source.find("{", decl.end())
    if open_brace == -1:
        return {"type": "object", "properties": {}}
    body = _extract_brace_block(source, open_brace)

    field_re = re.compile(
        r"^\s*(?:public|protected|private)?\s*(?:(?:static|final|transient|volatile)\s+)*"
        r"([\w\.$<>?,\[\]\s]+?)\s+"
        r"(\w+)\s*(?:=\s*[^;]+)?;\s*(?://.*)?$"
    )

    depth = 0
    for raw in body.splitlines():
        stripped = raw.strip()

        # Track brace depth (very lightweight; enough to distinguish class scope vs methods)
        # We evaluate candidate field lines using depth BEFORE applying this line's braces.
        current_depth = depth

        if stripped.startswith("@"):  # annotation
            if current_depth == 1:
                ann_buf.append(stripped)
        elif current_depth == 1:
            if stripped and ";" in stripped and "(" not in stripped:
                m = field_re.match(raw)
                if m:
                    java_type = m.group(1).strip()
                    name = m.group(2).strip()
                    if java_type.lower() in _JAVA_STATEMENT_KEYWORDS:
                        ann_buf = []
                    else:
                        is_required, constraints = _parse_constraints(ann_buf)
                        schema = java_type_to_schema(java_type)
                        if constraints:
                            schema = {**schema, **constraints}
                        properties[name] = schema
                        if is_required:
                            required.append(name)
                        ann_buf = []
            else:
                # Any other non-field line at class scope resets the annotation buffer
                if stripped and not stripped.startswith("//"):
                    ann_buf = []
        else:
            # Inside methods/inner blocks; ignore
            pass

        # Apply brace changes after processing the line
        # Remove simple line comments to avoid counting braces in comments.
        brace_scan = raw.split("//", 1)[0]
        depth += brace_scan.count("{")
        depth -= brace_scan.count("}")
        depth = max(0, depth)

    out: Dict[str, Any] = {"type": "object", "properties": properties}
    if required:
        out["required"] = sorted(set(required))
    return out


class JavaDTOExtractor:
    """Index Java sources and extract simple DTO schemas by class name."""

    def __init__(self, class_to_file: Dict[str, Path]):
        self.class_to_file = class_to_file
        self._schema_cache: Dict[str, Optional[Dict[str, Any]]] = {}

    @classmethod
    def from_java_files(cls, repo_root: Path, java_files: Iterable[Path]) -> "JavaDTOExtractor":
        index: Dict[str, Path] = {}

        class_re = re.compile(r"\b(class|record)\s+(\w+)\b")

        for file_path in java_files:
            try:
                p = Path(file_path)
                if not p.is_absolute():
                    p = repo_root / p
                if not p.exists():
                    continue
                text = p.read_text(encoding="utf-8", errors="ignore")
                for m in class_re.finditer(text):
                    index.setdefault(m.group(2), p)
            except Exception:
                continue

        return cls(index)

    def extract_schema(self, class_name: str, expand_refs_depth: int = 0) -> Optional[Dict[str, Any]]:
        """Extract a DTO schema by class/record name.

        Args:
            class_name: Java class/record name
            expand_refs_depth: If > 0, attempt to inline nested DTOs referenced via
                `x-java-type` up to the given depth.
        """
        base = (class_name or "").strip()
        if not base:
            return None

        schema = self._extract_schema_raw(base)
        if schema is None:
            return None
        if expand_refs_depth > 0:
            return self._expand_schema_refs(schema, depth=expand_refs_depth, visiting=set())
        return schema

    def _extract_schema_raw(self, class_name: str) -> Optional[Dict[str, Any]]:
        if class_name in self._schema_cache:
            return self._schema_cache[class_name]
        p = self.class_to_file.get(class_name)
        if not p:
            self._schema_cache[class_name] = None
            return None
        try:
            src = p.read_text(encoding="utf-8", errors="ignore")
            schema = extract_schema_from_java_source(src, class_name=class_name)
            self._schema_cache[class_name] = schema
            return schema
        except Exception:
            self._schema_cache[class_name] = None
            return None

    def _expand_schema_refs(self, schema: Any, depth: int, visiting: Set[str]) -> Any:
        if depth <= 0:
            return schema

        if isinstance(schema, list):
            return [self._expand_schema_refs(x, depth=depth, visiting=visiting) for x in schema]

        if not isinstance(schema, dict):
            return schema

        # Expand arrays / maps first
        if schema.get("type") == "array" and isinstance(schema.get("items"), dict):
            schema = dict(schema)
            schema["items"] = self._expand_schema_refs(schema["items"], depth=depth, visiting=visiting)
            return schema

        if schema.get("type") == "object" and isinstance(schema.get("additionalProperties"), dict):
            schema = dict(schema)
            schema["additionalProperties"] = self._expand_schema_refs(
                schema["additionalProperties"], depth=depth, visiting=visiting
            )
            return schema

        # Expand x-java-type objects with empty properties
        xjt = schema.get("x-java-type")
        if schema.get("type") == "object" and isinstance(xjt, str) and xjt:
            props = schema.get("properties")
            if not (isinstance(props, dict) and props):
                base = xjt.split(".")[-1].split("<", 1)[0].strip()
                if base and base not in visiting:
                    visiting.add(base)
                    resolved = self._extract_schema_raw(base)
                    visiting.remove(base)
                    if isinstance(resolved, dict) and isinstance(resolved.get("properties"), dict) and resolved["properties"]:
                        # Keep the x-java-type hint while enriching properties
                        merged = {**resolved, "x-java-type": xjt}
                        return self._expand_schema_refs(merged, depth=depth - 1, visiting=visiting)

        # Recurse into properties
        if schema.get("type") == "object" and isinstance(schema.get("properties"), dict):
            new_props: Dict[str, Any] = {}
            for k, v in schema["properties"].items():
                new_props[k] = self._expand_schema_refs(v, depth=depth, visiting=visiting)
            schema = dict(schema)
            schema["properties"] = new_props
            return schema

        return schema
