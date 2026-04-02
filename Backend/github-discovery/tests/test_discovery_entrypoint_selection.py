from pathlib import Path

import pytest

from src.config import Config
from src.pipeline import Pipeline


def _enrich(repo_root: Path, openapi_dict: dict) -> dict:
    pipeline = Pipeline(Config(llm_provider="none"))
    return pipeline._enrich_openapi_with_discovery(
        openapi_dict,
        repo_url="https://example.com/org/repo.git",
        branch="main",
        repo_root=repo_root,
    )


def test_entrypoint_selection_avoids_postgres(tmp_path: Path):
    repo_root = tmp_path
    (repo_root / "docker-compose.yml").write_text(
        """
        version: '3.8'
        services:
          postgres:
            image: postgres:15
            ports:
              - '5432:5432'
          api:
            build: ./api
            ports:
              - '8080:8080'
        """,
        encoding="utf-8",
    )

    openapi_dict = {
        "openapi": "3.0.3",
        "info": {"title": "t", "version": "1"},
        "paths": {"/health": {"get": {"responses": {"200": {"description": "ok"}}}}},
    }

    enriched = _enrich(repo_root, openapi_dict)

    assert enriched["x-discovery"]["run"]["api_service"] == "api"
    assert enriched["x-discovery"]["run"]["published_port"] == 8080
    assert enriched["x-discovery"]["run"]["base_url"] == "http://localhost:8080"
    assert enriched["servers"][0]["url"] == "http://localhost:8080"
    assert enriched["info"]["x-discovery-base-url"] == "http://localhost:8080"

    http_services = enriched["x-discovery"]["run"]["http_services"]
    assert any(s["service"] == "api" and s["base_url"] == "http://localhost:8080" for s in http_services)
    assert not any(s["service"] == "postgres" for s in http_services)


def test_entrypoint_selection_prefers_gateway(tmp_path: Path):
    repo_root = tmp_path
    (repo_root / "docker-compose.yml").write_text(
        """
        version: '3.8'
        services:
          mysql:
            image: mysql:8
            ports:
              - '3306:3306'
          users-service:
            build: ./users
            ports:
              - '8081:8081'
          gateway:
            build: ./gateway
            ports:
              - '8089:8089'
        """,
        encoding="utf-8",
    )

    openapi_dict = {
        "openapi": "3.0.3",
        "info": {"title": "t", "version": "1"},
        "paths": {"/actuator/health": {"get": {"responses": {"200": {"description": "ok"}}}}},
    }

    enriched = _enrich(repo_root, openapi_dict)

    assert enriched["x-discovery"]["run"]["api_service"] == "gateway"
    assert enriched["x-discovery"]["run"]["published_port"] == 8089
    assert enriched["x-discovery"]["run"]["base_url"] == "http://localhost:8089"
    assert enriched["x-discovery"]["run"]["healthcheck_path"] == "/actuator/health"
    assert enriched["servers"][0]["url"] == "http://localhost:8089"

    http_services = enriched["x-discovery"]["run"]["http_services"]
    assert http_services[0]["service"] == "gateway"
    assert any(s["service"] == "users-service" for s in http_services)


def test_infra_only_compose_has_no_api_entrypoint(tmp_path: Path):
    repo_root = tmp_path
    (repo_root / "docker-compose.yml").write_text(
        """
        version: '3.8'
        services:
          postgres:
            image: postgres:15
            ports:
              - '5432:5432'
          pgadmin:
            image: dpage/pgadmin4
            ports:
              - '5050:80'
        """,
        encoding="utf-8",
    )

    openapi_dict = {
        "openapi": "3.0.3",
        "info": {"title": "t", "version": "1"},
        "paths": {"/health": {"get": {"responses": {"200": {"description": "ok"}}}}},
    }

    enriched = _enrich(repo_root, openapi_dict)
    run = enriched["x-discovery"]["run"]

    assert run["api_service"] is None
    assert run["base_url"] is None
    assert run["published_port"] is None
    assert "run.api_service" in enriched["x-discovery"]["discovery"]["missing"]
    assert "run.base_url" in enriched["x-discovery"]["discovery"]["missing"]

    assert enriched["x-discovery"]["run"]["http_services"] == []

