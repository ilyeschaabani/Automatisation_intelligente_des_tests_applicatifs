from src.contract_patch import apply_discovery_patch


def test_apply_patch_aligns_servers_and_recomputes_missing():
    openapi = {
        "openapi": "3.0.3",
        "servers": [{"url": "http://localhost"}],
        "info": {"title": "x"},
        "paths": {"/api/auth/signin": {"post": {}}},
        "x-discovery": {
            "db": {"required": False, "type": None, "service": None, "url_env_var": None},
            "run": {
                "compose_path": "infrastructure/docker-compose.yml",
                "api_service": None,
                "base_url": None,
                "healthcheck_path": "/health",
            },
            "auth": {
                "type": "login_flow",
                "login": {"endpoint": "/api/auth/signin", "username": None, "password": None},
                "static_token": None,
                "header_name": "Authorization",
                "header_template": "Bearer {token}",
            },
            "discovery": {"missing": ["run.base_url", "auth.login.username"], "notes": []},
        },
    }

    patch = {
        "x-discovery": {
            "run": {"api_service": "auth", "base_url": "http://localhost:8080"},
            "auth": {"login": {"username": "u", "password": "p"}},
        }
    }

    updated = apply_discovery_patch(openapi, patch)

    assert updated["servers"][0]["url"] == "http://localhost:8080"
    assert updated["info"]["x-discovery-base-url"] == "http://localhost:8080"

    missing = updated["x-discovery"]["discovery"]["missing"]
    assert "run.base_url" not in missing
    assert "auth.login.username" not in missing
    assert "auth.login.password" not in missing
