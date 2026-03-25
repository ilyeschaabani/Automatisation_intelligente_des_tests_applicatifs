from pathlib import Path

from src.ast_parser import TreeSitterParser
from src.config import Config
from src.models.openapi import OpenAPISpec
from src.pipeline import Pipeline


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
    pipeline = Pipeline(Config(llm_provider="none"))
    pipeline._enrich_spring_requests(endpoints, repo_root, [controller, dto])
    pipeline._ensure_request_templates(endpoints)

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

    # Example should be non-empty now that schema has properties
    assert isinstance(body.get("example"), dict)
    assert body["example"].get("email")

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


def test_spring_generic_body_schema(tmp_path: Path):
    repo_root = tmp_path

    controller = repo_root / "IdsController.java"
    controller.write_text(
        """
        package com.example.api;

        import org.springframework.web.bind.annotation.*;
        import java.util.*;

        @RestController
        @RequestMapping(\"/api/ids\")
        public class IdsController {

            @PostMapping(\"/bulk\")
            public String bulk(@RequestBody Set<Long> ids) {
                return \"ok\";
            }
        }
        """,
        encoding="utf-8",
    )

    parser = TreeSitterParser()
    endpoints = parser.parse_file(controller, "java").endpoints
    assert len(endpoints) == 1

    pipeline = Pipeline(Config(llm_provider="none"))
    pipeline._enrich_spring_requests(endpoints, repo_root, [controller])
    pipeline._ensure_request_templates(endpoints)

    ep = endpoints[0]
    req = ep.metadata.get("request")
    assert isinstance(req, dict)
    body = req.get("body")
    assert isinstance(body, dict)

    schema = body.get("schema")
    assert isinstance(schema, dict)
    assert schema["type"] == "array"
    assert schema["items"]["type"] == "integer"
    assert schema["items"].get("format") == "int64"

    example = body.get("example")
    assert isinstance(example, list)
    assert len(example) == 1
