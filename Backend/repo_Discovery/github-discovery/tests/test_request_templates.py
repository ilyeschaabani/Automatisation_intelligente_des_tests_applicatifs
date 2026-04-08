import json
from pathlib import Path

from src.config import Config
from src.layered_extractor import SpecFirstExtractor
from src.models.endpoint import Endpoint, HTTPMethod
from src.pipeline import Pipeline


def test_ensure_request_templates_adds_path_and_body():
    ep = Endpoint(
        method=HTTPMethod.PUT,
        path="/users/{id}",
        file_path="x",
        line_number=1,
        parameters=["id", "payload"],
        metadata={},
    )

    p = Pipeline(Config())
    p._ensure_request_templates([ep])

    req = ep.metadata.get("request")
    assert isinstance(req, dict)
    assert any(it.get("name") == "id" for it in req.get("path", []))
    assert isinstance(req.get("body"), dict)
    assert req["body"].get("content_type") == "application/json"


def test_ensure_request_templates_does_not_invent_body_when_no_candidates():
    ep = Endpoint(
        method=HTTPMethod.POST,
        path="/users/{id}/verify",
        file_path="x",
        line_number=1,
        parameters=["id"],
        metadata={},
    )

    p = Pipeline(Config())
    p._ensure_request_templates([ep])

    req = ep.metadata.get("request")
    assert isinstance(req, dict)
    assert any(it.get("name") == "id" for it in req.get("path", []))
    assert req.get("body") is None


def test_spec_first_extracts_request_meta(tmp_path: Path):
    spec = {
        "openapi": "3.0.3",
        "info": {"title": "T", "version": "1"},
        "paths": {
            "/users/{id}": {
                "parameters": [
                    {"name": "id", "in": "path", "required": True, "schema": {"type": "string"}},
                ],
                "put": {
                    "operationId": "updateUser",
                    "parameters": [
                        {"name": "verbose", "in": "query", "required": False, "schema": {"type": "boolean"}},
                    ],
                    "requestBody": {
                        "required": True,
                        "content": {
                            "application/json": {
                                "schema": {"type": "object", "properties": {"name": {"type": "string"}}},
                                "example": {"name": "alice"},
                            }
                        },
                    },
                    "responses": {"200": {"description": "ok"}},
                },
            }
        },
    }

    repo = tmp_path / "repo"
    repo.mkdir()
    (repo / "openapi.json").write_text(json.dumps(spec), encoding="utf-8")

    extractor = SpecFirstExtractor()
    result = extractor.try_extract(repo)
    assert result is not None
    assert result.short_circuit is True
    assert len(result.endpoints) == 1

    ep = result.endpoints[0]
    req = ep.metadata.get("request")
    assert isinstance(req, dict)
    assert any(it.get("name") == "id" for it in req.get("path", []))
    assert any(it.get("name") == "verbose" for it in req.get("query", []))
    body = req.get("body")
    assert isinstance(body, dict)
    assert body.get("content_type") == "application/json"
    assert body.get("example") == {"name": "alice"}
