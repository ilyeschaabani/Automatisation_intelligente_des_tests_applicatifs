from src.config import Config
from src.models.endpoint import Endpoint, HTTPMethod
from src.pipeline import Pipeline


def test_pipeline_filters_gateway_wildcards():
    pipeline = Pipeline(Config(llm_provider="none"))

    endpoints = [
        Endpoint(
            method=HTTPMethod.GET,
            path="/api/auth/**",
            file_path="GatewayApplication.java",
            line_number=1,
            confidence=0.6,
            source="regex",
            metadata={"pattern": "spring_cloud_gateway_route"},
        ),
        Endpoint(
            method=HTTPMethod.GET,
            path="/api/users",
            file_path="UserController.java",
            line_number=1,
            confidence=0.9,
            source="ast",
            metadata={"pattern": "spring_GetMapping"},
        ),
        Endpoint(
            method=HTTPMethod.GET,
            path="/anything/**",
            file_path="application.properties",
            line_number=1,
            confidence=0.7,
            source="config",
            metadata={"pattern": "spring_cloud_gateway_path_predicate"},
        ),
    ]

    filtered = pipeline._filter_gateway_wildcard_endpoints(endpoints)

    assert [e.path for e in filtered] == ["/api/users"]
