import json
from pathlib import Path

import pytest

from src.layered_extractor import SpecFirstExtractor, ConfigRoutesExtractor, UniversalFallbackExtractor


def test_layer1_spec_first_openapi_json(tmp_path: Path):
    repo = tmp_path / "repo"
    repo.mkdir()

    spec_path = repo / "openapi.json"
    spec_path.write_text(
        json.dumps(
            {
                "openapi": "3.0.3",
                "info": {"title": "T", "version": "1"},
                "paths": {
                    "/users": {"get": {"operationId": "getUsers"}},
                    "/users/{id}": {"delete": {"operationId": "deleteUser"}},
                },
            }
        ),
        encoding="utf-8",
    )

    extractor = SpecFirstExtractor()
    result = extractor.try_extract(repo)

    assert result is not None
    assert result.short_circuit is True
    assert result.openapi is not None
    assert len(result.endpoints) == 2
    paths = {(e.method.value, e.path) for e in result.endpoints}
    assert ("get", "/users") in paths
    assert ("delete", "/users/{id}") in paths


def test_layer2_config_routes_symfony_yaml(tmp_path: Path):
    repo = tmp_path / "repo"
    repo.mkdir()

    routes = repo / "routes.yaml"
    routes.write_text(
        """
home:
  path: /home
  controller: App\\Controller\\HomeController::index

api_users:
  path: /api/users
  methods: [GET, POST]
  controller: App\\Controller\\UserController::list
""",
        encoding="utf-8",
    )

    extractor = ConfigRoutesExtractor()
    endpoints, parsed = extractor.extract(repo)

    assert parsed >= 1
    assert any(e.path == "/home" for e in endpoints)
    api_methods = sorted([e.method.value for e in endpoints if e.path == "/api/users"])
    assert api_methods == ["get", "post"]


def test_layer2_config_routes_rails_routes_rb(tmp_path: Path):
    repo = tmp_path / "repo"
    (repo / "config").mkdir(parents=True)

    routes = repo / "config" / "routes.rb"
    routes.write_text(
        """
Rails.application.routes.draw do
  get '/health', to: 'health#show'
  post 'api/login', to: 'auth#login'
  match '/multi', to: 'x#y', via: [:get, :delete]
end
""",
        encoding="utf-8",
    )

    extractor = ConfigRoutesExtractor()
    endpoints, parsed = extractor.extract(repo)

    assert parsed >= 1
    paths = {(e.method.value, e.path) for e in endpoints}
    assert ("get", "/health") in paths
    assert ("post", "/api/login") in paths
    assert ("get", "/multi") in paths
    assert ("delete", "/multi") in paths


def test_layer4_universal_fallback(tmp_path: Path):
    repo = tmp_path / "repo"
    repo.mkdir()

    f = repo / "README.md"
    f.write_text("Use GET /api/users and POST /api/users to manage users", encoding="utf-8")

    extractor = UniversalFallbackExtractor()
    endpoints, scanned = extractor.extract(repo)

    assert scanned >= 1
    paths = {(e.method.value, e.path) for e in endpoints}
    assert ("get", "/api/users") in paths
    assert ("post", "/api/users") in paths
