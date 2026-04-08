from src.contract_interview import build_findings, render_questionnaire


def test_infra_only_compose_flags_missing_base_url_when_paths_exist():
    openapi = {
        "openapi": "3.0.3",
        "servers": [{"url": "http://localhost"}],
        "paths": {"/api/auth/signin": {"post": {}}},
        "x-discovery": {
            "db": {"required": False, "type": None, "service": None, "url_env_var": None},
            "run": {
                "compose_path": None,
                "compose_candidates": ["infrastructure/docker-compose.yml"],
                "api_service": None,
                "base_url": None,
                "http_services": [],
            },
            "services": {},
            "discovery": {"missing": ["run.compose_path", "run.api_service", "run.base_url"]},
        },
    }

    findings = build_findings(openapi)
    missing_paths = {f.json_path for f in findings if f.kind == "missing"}
    low_conf_paths = {f.json_path for f in findings if f.kind == "low_confidence"}

    assert "$.x-discovery.run.base_url" in missing_paths
    assert "$.x-discovery.run.api_service" in missing_paths
    assert "$.x-discovery.run.compose_path" in missing_paths

    questionnaire = render_questionnaire(openapi, findings)
    assert isinstance(questionnaire.get("questions"), list)
    qs = questionnaire["questions"]
    assert any(q.get("json_path") == "$.x-discovery.run.compose_path" for q in qs)


def test_multi_service_requires_routing_rules():
    openapi = {
        "openapi": "3.0.3",
        "servers": [{"url": "http://localhost"}],
        "paths": {"/users": {"get": {}}, "/orders": {"get": {}}},
        "x-discovery": {
            "db": {"required": False, "type": None, "service": None, "url_env_var": None},
            "run": {
                "compose_path": "docker-compose.yml",
                "api_service": None,
                "base_url": None,
                "http_services": [
                    {"service": "user-service", "base_url": "http://localhost:8081"},
                    {"service": "order-service", "base_url": "http://localhost:8082"},
                ],
            },
            "services": {
                "user-service": {"is_infra": False},
                "order-service": {"is_infra": False},
            },
            "discovery": {"missing": []},
        },
    }

    findings = build_findings(openapi)
    assert any(
        f.kind == "missing" and f.json_path == "$.x-discovery.routing.rules" for f in findings
    )
