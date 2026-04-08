"""Tests for OpenAPI generator"""

import pytest
from src.models.endpoint import Endpoint, HTTPMethod
from src.models.openapi import OpenAPISpec


def test_openapi_from_endpoints():
    """Test creating OpenAPI spec from endpoints"""
    endpoints = [
        Endpoint(
            method=HTTPMethod.GET,
            path="/users",
            file_path="/app/routes.js",
            line_number=10,
            function_name="getUsers"
        ),
        Endpoint(
            method=HTTPMethod.POST,
            path="/users",
            file_path="/app/routes.js",
            line_number=20,
            function_name="createUser"
        ),
        Endpoint(
            method=HTTPMethod.GET,
            path="/users/:id",
            file_path="/app/routes.js",
            line_number=30,
            function_name="getUser",
            parameters=["id"]
        ),
    ]

    spec = OpenAPISpec.from_endpoints(
        endpoints,
        title="Test API",
        version="2.0.0",
        description="Test description"
    )

    spec_dict = spec.to_dict()

    assert spec_dict["openapi"] == "3.0.3"
    assert spec_dict["info"]["title"] == "Test API"
    assert spec_dict["info"]["version"] == "2.0.0"
    assert spec_dict["info"]["description"] == "Test description"
    # There should be 2 paths: /users and /users/{id}
    assert len(spec_dict["paths"]) == 2, f"Expected 2 paths, got {list(spec_dict['paths'].keys())}"
    assert "/users" in spec_dict["paths"]
    assert "/users/{id}" in spec_dict["paths"]  # Normalized

    # Check methods on /users
    assert "get" in spec_dict["paths"]["/users"]
    assert "post" in spec_dict["paths"]["/users"]

    # Check parameters
    assert "parameters" in spec_dict["paths"]["/users/{id}"]["get"]


def test_openapi_with_router_prefix():
    """Test OpenAPI with router prefixes"""
    endpoints = [
        Endpoint(
            method=HTTPMethod.GET,
            path="/users",
            file_path="/app/routes.js",
            line_number=10,
            router_prefix="/api/v1",
            full_path="/api/v1/users"
        ),
    ]

    spec = OpenAPISpec.from_endpoints(endpoints)
    spec_dict = spec.to_dict()

    assert "/api/v1/users" in spec_dict["paths"]


def test_openapi_tags():
    """Test tag generation"""
    endpoints = [
        Endpoint(
            method=HTTPMethod.GET,
            path="/users",
            file_path="/app/controllers/user_controller.js",
            line_number=10,
            function_name="getUsers"
        ),
    ]

    spec = OpenAPISpec.from_endpoints(endpoints)
    spec_dict = spec.to_dict()

    assert len(spec_dict["tags"]) > 0
    # Should have tag based on file path
    tag_names = [t["name"] for t in spec_dict["tags"]]
    assert "user" in tag_names or "controller" in tag_names or "api" in tag_names


def test_openapi_validation():
    """Test that generated OpenAPI is valid"""
    from openapi_spec_validator import validate_spec

    endpoints = [
        Endpoint(
            method=HTTPMethod.GET,
            path="/test",
            file_path="/test.js",
            line_number=1
        ),
    ]

    spec = OpenAPISpec.from_endpoints(endpoints)
    spec_dict = spec.to_dict()

    # Should not raise
    validate_spec(spec_dict)