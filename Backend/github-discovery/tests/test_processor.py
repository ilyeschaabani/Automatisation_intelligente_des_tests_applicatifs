"""Tests for endpoint processor"""

import pytest
from src.models.endpoint import Endpoint, HTTPMethod
from src.endpoint_processor import EndpointProcessor


def test_normalize_paths():
    """Test path normalization"""
    processor = EndpointProcessor()

    endpoints = [
        Endpoint(
            method=HTTPMethod.GET,
            path="users",  # No leading slash
            file_path="/test.js",
            line_number=1
        ),
        Endpoint(
            method=HTTPMethod.POST,
            path="/api/posts//",  # Double slash, trailing slash
            file_path="/test.js",
            line_number=10
        ),
    ]

    result = processor.process(endpoints)
    normalized = result.endpoints

    assert normalized[0].path == "/users"
    assert normalized[1].path == "/api/posts"


def test_deduplication():
    """Test duplicate removal"""
    processor = EndpointProcessor()

    endpoints = [
        Endpoint(method=HTTPMethod.GET, path="/users", file_path="/a.js", line_number=1),
        Endpoint(method=HTTPMethod.GET, path="/users", file_path="/b.js", line_number=5),  # Duplicate
        Endpoint(method=HTTPMethod.POST, path="/users", file_path="/c.js", line_number=10),  # Different method
    ]

    result = processor.process(endpoints)

    assert len(result.endpoints) == 2
    assert result.duplicates_removed == 1


def test_router_prefix_resolution():
    """Test router prefix resolution"""
    processor = EndpointProcessor()

    endpoints = [
        Endpoint(
            method=HTTPMethod.GET,
            path="/users",
            file_path="/api/routes.js",
            line_number=1,
            router_prefix="/api/v1"
        ),
    ]

    prefix_mapping = {"/api/routes.js": "/api/v1"}

    result = processor.process(endpoints, prefix_mapping)

    assert result.endpoints[0].full_path == "/api/v1/users"


def test_path_combination():
    """Test path combination logic"""
    processor = EndpointProcessor()

    assert processor._combine_paths("/api", "/users") == "/api/users"
    assert processor._combine_paths("/api/", "/users") == "/api/users"
    assert processor._combine_paths("/api", "users") == "/api/users"
    assert processor._combine_paths("/api/v1", "/users/:id") == "/api/v1/users/:id"
    assert processor._combine_paths("", "/users") == "/users"
    assert processor._combine_paths("/api", "") == "/api"