from pathlib import Path

from src.ast_parser import TreeSitterParser
from src.java_dto_extractor import JavaDTOExtractor
from src.models.openapi import OpenAPISpec
from src.java_dto_extractor import java_type_to_schema


def _enrich_like_pipeline(endpoints, repo_root: Path, candidate_files: list[Path]) -> None:
    java_files = [p for p in candidate_files if p.suffix.lower() == ".java"]
    dto_extractor = JavaDTOExtractor.from_java_files(repo_root, java_files)

    for ep in endpoints:
        meta = ep.metadata or {}
        req = meta.get("request")
        if not isinstance(req, dict):
            continue

        for section in ("path", "query", "header"):
            items = req.get(section)
            if not isinstance(items, list):
                continue
            for item in items:
                if not isinstance(item, dict):
                    continue
                jt = item.get("java_type") or "string"
                item.setdefault("schema", java_type_to_schema(str(jt)))
                if section == "path":
                    item["required"] = True
                else:
                    item.setdefault("required", True)

        body = req.get("body")
        if isinstance(body, dict):
            dto_type = (body.get("dto_type") or "").strip()
            dto_simple = dto_type.split(".")[-1].split("<", 1)[0].strip()
            schema = dto_extractor.extract_schema(dto_simple)
            body["schema"] = schema or {"type": "object"}

        ep.metadata = meta


def test_spring_request_meta_and_openapi(tmp_path: Path):
    repo_root = tmp_path

    controller = repo_root / "UserController.java"
    controller.write_text(
        """
        package com.example.api;

        import org.springframework.web.bind.annotation.*;
        import javax.validation.Valid;

        @RestController
        @RequestMapping(\"/api/users\")
        public class UserController {

            @PostMapping(\"/create\")
            public String create(
                @RequestHeader(value=\"X-Token\") String token,
                @RequestParam(required=false) Integer page,
                @Valid @RequestBody CreateUserRequest body
            ) {
                return \"ok\";
            }

            @GetMapping(\"/{id}\")
            public String getById(@PathVariable(\"id\") Long id) {
                return \"ok\";
            }
        }
        """,
        encoding="utf-8",
    )

    dto = repo_root / "CreateUserRequest.java"
    dto.write_text(
        """
        package com.example.api;

        import javax.validation.constraints.*;
        import java.util.*;

        public class CreateUserRequest {
            @NotBlank
            private String email;

            @NotNull
            private Integer age;

            @Size(min=1)
            private List<String> roles;
        }
        """,
        encoding="utf-8",
    )

    parser = TreeSitterParser()
    res = parser.parse_file(controller, "java")
    endpoints = res.endpoints
    assert len(endpoints) == 2

    # Enrich like pipeline does
    _enrich_like_pipeline(endpoints, repo_root, [controller, dto])

    # Find POST /create
    post_ep = next(e for e in endpoints if e.method.value == "post")
    req = post_ep.metadata.get("request")
    assert req is not None

    # Check query/header params exist
    assert any(p["name"] == "X-Token" for p in req["header"])
    assert any(p["name"] == "page" for p in req["query"])

    # Body schema fields
    body = req["body"]
    assert body["content_type"] == "application/json"
    schema = body["schema"]
    assert schema["type"] == "object"
    assert "email" in schema["properties"]
    assert "age" in schema["properties"]
    assert set(schema.get("required", [])) >= {"email", "age"}

    # OpenAPI should include requestBody + parameters
    spec = OpenAPISpec.from_endpoints(endpoints)
    out = spec.to_dict()
    op = out["paths"]["/api/users/create"]["post"]

    assert "requestBody" in op
    assert "application/json" in op["requestBody"]["content"]

    params = op.get("parameters", [])
    assert any(p["in"] == "header" and p["name"] == "X-Token" for p in params)
    assert any(p["in"] == "query" and p["name"] == "page" for p in params)

    get_op = out["paths"]["/api/users/{id}"]["get"]
    get_params = get_op.get("parameters", [])
    assert any(p["in"] == "path" and p["name"] == "id" and p["required"] is True for p in get_params)
