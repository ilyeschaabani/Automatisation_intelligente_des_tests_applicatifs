import json

import pytest
from fastapi.testclient import TestClient


@pytest.fixture()
def client(monkeypatch, tmp_path):
    # Force the API to read/write OpenAPI artifacts in a temp directory.
    monkeypatch.setenv("OUTPUT_DIR", str(tmp_path))

    import discovery_api

    # Clear in-memory job store between tests
    discovery_api.jobs.clear()

    return TestClient(discovery_api.app)


def _write_openapi(tmp_path, repo_id: str, payload: dict) -> None:
    p = tmp_path / f"{repo_id}_openapi.json"
    p.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def test_questionnaire_and_complete_aligns_servers(client, tmp_path):
    import discovery_api

    job_id = "job1"
    repo_id = "r1"

    # Minimal OpenAPI with x-discovery and a missing base_url.
    openapi_obj = {
        "openapi": "3.0.3",
        "info": {"title": "t", "version": "1.0.0"},
        "servers": [{"url": "http://localhost"}],
        "paths": {"/ping": {"get": {"responses": {"200": {"description": "ok"}}}}},
        "x-discovery": {
            "run": {
                "strategy": "docker_compose",
                "compose_path": "docker-compose.yml",
                "api_service": "api",
                "published_port": None,
                "base_url": None,
                "healthcheck_path": "/health",
                "http_services": [],
            },
            "auth": {"type": "unknown"},
            "services": {},
            "secrets_env": {"required_env_vars": []},
            "discovery": {"missing": ["run.base_url"], "notes": []},
        },
    }

    _write_openapi(tmp_path, repo_id, openapi_obj)

    discovery_api.jobs[job_id] = {
        "job_id": job_id,
        "repo_url": "https://example.com/x.git",
        "branch": None,
        "repo_id": repo_id,
        "status": "done",
        "created_at": "2026-01-01T00:00:00Z",
        "error": None,
        "stats": None,
    }

    q = client.get(f"/discovery/jobs/{job_id}/contract/questionnaire")
    assert q.status_code == 200, q.text
    body = q.json()
    assert body["repo_id"] == repo_id
    questions = body["questionnaire"]["questions"]
    assert any(item["json_path"] == "$.x-discovery.run.base_url" for item in questions)

    # Post user answers and ensure servers[0].url aligns to base_url.
    r = client.post(
        f"/discovery/jobs/{job_id}/complete",
        json={
            "answers": [{"json_path": "$.x-discovery.run.base_url", "value": "http://localhost:8080"}],
            "overwrite": True,
            "return_openapi": True,
        },
    )
    assert r.status_code == 200

    updated = r.json()["openapi"]
    assert updated["x-discovery"]["run"]["base_url"] == "http://localhost:8080"
    assert updated["servers"][0]["url"] == "http://localhost:8080"

    # Verify on-disk file updated too.
    on_disk = json.loads((tmp_path / f"{repo_id}_openapi.json").read_text(encoding="utf-8"))
    assert on_disk["servers"][0]["url"] == "http://localhost:8080"


def test_questionnaire_includes_healthcheck_when_missing(client, tmp_path):
    import discovery_api

    job_id = "job2"
    repo_id = "r2"

    openapi_obj = {
        "openapi": "3.0.3",
        "info": {"title": "t", "version": "1.0.0"},
        "servers": [{"url": "http://localhost:8080"}],
        "paths": {"/ping": {"get": {"responses": {"200": {"description": "ok"}}}}},
        "x-discovery": {
            "run": {
                "strategy": "docker_compose",
                "compose_path": "docker-compose.yml",
                "api_service": "api",
                "published_port": 8080,
                "base_url": "http://localhost:8080",
                "healthcheck_path": None,
                "http_services": [],
            },
            "auth": {"type": "none"},
            "services": {},
            "secrets_env": {"required_env_vars": []},
            "discovery": {"missing": ["run.healthcheck_path"], "notes": []},
        },
    }

    _write_openapi(tmp_path, repo_id, openapi_obj)

    discovery_api.jobs[job_id] = {
        "job_id": job_id,
        "repo_url": "https://example.com/x.git",
        "branch": None,
        "repo_id": repo_id,
        "status": "done",
        "created_at": "2026-01-01T00:00:00Z",
        "error": None,
        "stats": None,
    }

    q = client.get(f"/discovery/jobs/{job_id}/contract/questionnaire")
    assert q.status_code == 200, q.text
    body = q.json()
    questions = body["questionnaire"]["questions"]
    assert any(item["json_path"] == "$.x-discovery.run.healthcheck_path" for item in questions)
