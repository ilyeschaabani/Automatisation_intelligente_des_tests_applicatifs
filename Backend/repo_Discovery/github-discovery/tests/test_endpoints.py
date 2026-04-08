"""Tests for endpoint model"""

import pytest
from src.models.endpoint import Endpoint, HTTPMethod


def test_endpoint_creation():
    """Test basic endpoint creation"""
    ep = Endpoint(
        method=HTTPMethod.GET,
        path="/users",
        file_path="/app/routes.js",
        line_number=10
    )
    assert ep.method == HTTPMethod.GET
    assert ep.path == "/users"
    assert ep.full_path == "/users"


def test_endpoint_with_prefix():
    """Test endpoint with router prefix"""
    ep = Endpoint(
        method=HTTPMethod.POST,
        path="/create",
        file_path="/app/routes.js",
        line_number=20,
        router_prefix="/api/v1"
    )
    assert ep.full_path == "/api/v1/create"


def test_endpoint_normalization():
    """Test path normalization"""
    ep = Endpoint(
        method=HTTPMethod.GET,
        path="users",  # No leading slash
        file_path="/app/routes.js",
        line_number=5
    )
    assert ep.path == "/users"
    assert ep.full_path == "/users"


def test_endpoint_dedup_key():
    """Test deduplication key generation"""
    ep1 = Endpoint(
        method=HTTPMethod.GET,
        path="/users",
        file_path="/app/routes1.js",
        line_number=10
    )
    ep2 = Endpoint(
        method=HTTPMethod.GET,
        path="/users",
        file_path="/app/routes2.js",
        line_number=20
    )
    assert ep1.get_dedup_key() == ep2.get_dedup_key()


def test_endpoint_to_dict():
    """Test endpoint serialization"""
    ep = Endpoint(
        method=HTTPMethod.DELETE,
        path="/users/:id",
        file_path="/app/routes.js",
        line_number=30,
        function_name="deleteUser",
        parameters=["id"],
        confidence=0.9,
        source="ast"
    )
    data = ep.to_dict()
    assert data["method"] == "delete"
    assert data["path"] == "/users/:id"
    assert data["function_name"] == "deleteUser"
    assert data["parameters"] == ["id"]
    assert data["confidence"] == 0.9
    assert data["source"] == "ast"


def test_endpoint_from_dict():
    """Test endpoint deserialization"""
    data = {
        "method": "post",
        "path": "/users",
        "file_path": "/app/routes.js",
        "line_number": 15,
        "function_name": "createUser",
        "parameters": [],
        "confidence": 1.0,
        "source": "ast"
    }
    ep = Endpoint.from_dict(data)
    assert ep.method == HTTPMethod.POST
    assert ep.path == "/users"
    assert ep.function_name == "createUser"