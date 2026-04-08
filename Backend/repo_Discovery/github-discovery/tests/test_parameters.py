from src.endpoint_processor import EndpointProcessor
from src.models.endpoint import Endpoint, HTTPMethod


def test_parameters_include_path_params_and_filter_noise():
    ep = Endpoint(
        method=HTTPMethod.GET,
        path="/users/:id",
        file_path="x.js",
        line_number=1,
        router_prefix="/api",
        function_name="getUser",
        parameters=["req", "res", "next"],
        confidence=0.5,
        source="regex",
    )

    processor = EndpointProcessor()
    result = processor.process([ep], router_prefixes={})

    assert len(result.endpoints) == 1
    out = result.endpoints[0]
    assert out.full_path == "/api/users/:id"
    assert out.parameters == ["id"]


def test_parameters_merge_signature_and_path_params():
    ep = Endpoint(
        method=HTTPMethod.GET,
        path="/users/{id}",
        file_path="x.py",
        line_number=10,
        router_prefix="/v1",
        function_name="read_user",
        parameters=["self", "id", "q"],
        confidence=0.9,
        source="ast",
    )

    processor = EndpointProcessor()
    result = processor.process([ep], router_prefixes={})

    out = result.endpoints[0]
    assert out.full_path == "/v1/users/{id}"
    # Ensure path params come first and "self" is filtered
    assert out.parameters == ["id", "q"]
