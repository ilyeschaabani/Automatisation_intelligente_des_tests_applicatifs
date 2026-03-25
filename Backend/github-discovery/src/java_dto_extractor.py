from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple


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


def extract_schema_from_java_source(source: str) -> Dict:
    """Heuristic DTO schema extraction from Java source.

    Handles simple fields like:
      @NotNull
      private Integer age;

    Also extracts a few constraints:
      @Size(min=1,max=10), @Email, @Min/@Max
    """
    properties: Dict[str, Dict] = {}
    required: List[str] = []

    lines = source.splitlines()
    ann_buf: List[str] = []

    field_re = re.compile(
        r"^\s*(?:public|protected|private)?\s*(?:final\s+)?([\w\.<>,\[\]\s]+)\s+(\w+)\s*;\s*$"
    )

    def parse_constraints(annotations: List[str]) -> Tuple[bool, Dict]:
        is_required = False
        constraints: Dict = {}

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

    for raw in lines:
        line = raw.strip()
        if not line:
            ann_buf = []
            continue

        if line.startswith("@"):  # annotation
            ann_buf.append(line)
            continue

        m = field_re.match(raw)
        if not m:
            ann_buf = []
            continue

        java_type = m.group(1).strip()
        name = m.group(2).strip()

        is_required, constraints = parse_constraints(ann_buf)

        schema = java_type_to_schema(java_type)
        if constraints:
            schema = {**schema, **constraints}

        properties[name] = schema
        if is_required:
            required.append(name)

        ann_buf = []

    out: Dict = {"type": "object", "properties": properties}
    if required:
        out["required"] = sorted(set(required))
    return out


class JavaDTOExtractor:
    """Index Java sources and extract simple DTO schemas by class name."""

    def __init__(self, class_to_file: Dict[str, Path]):
        self.class_to_file = class_to_file

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

    def extract_schema(self, class_name: str) -> Optional[Dict]:
        p = self.class_to_file.get(class_name)
        if not p:
            return None
        try:
            src = p.read_text(encoding="utf-8", errors="ignore")
            return extract_schema_from_java_source(src)
        except Exception:
            return None
