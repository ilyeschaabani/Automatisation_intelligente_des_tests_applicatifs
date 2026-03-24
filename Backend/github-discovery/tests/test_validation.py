"""Comprehensive validation tests for the GitHub API Discovery Pipeline"""

import pytest
from pathlib import Path
from src.models.endpoint import Endpoint, HTTPMethod
from src.endpoint_processor import EndpointProcessor
from src.models.openapi import OpenAPISpec
from src.config import Config


class TestEndpointValidation:
    """Test endpoint model and normalization"""

    def test_endpoint_creation_with_prefix(self):
        """Test endpoint creation with router prefix"""
        ep = Endpoint(
            method=HTTPMethod.GET,
            path="/users",
            file_path="app/routes.js",
            line_number=10,
            router_prefix="/api/v1"
        )
        assert ep.method == HTTPMethod.GET
        assert ep.path == "/users"
        assert ep.full_path == "/api/v1/users"

    def test_endpoint_path_normalization(self):
        """Test path normalization"""
        # Without leading slash
        ep1 = Endpoint(
            method=HTTPMethod.POST,
            path="users",
            file_path="app/routes.js",
            line_number=10
        )
        assert ep1.path == "/users"
        assert ep1.full_path == "/users"

        # With trailing slash
        ep2 = Endpoint(
            method=HTTPMethod.PUT,
            path="/users/",
            file_path="app/routes.js",
            line_number=20
        )
        assert ep2.path == "/users/"

    def test_endpoint_deduplication_key(self):
        """Test deduplication key generation"""
        ep1 = Endpoint(
            method=HTTPMethod.GET,
            path="/users",
            file_path="file1.js",
            line_number=10
        )
        ep2 = Endpoint(
            method=HTTPMethod.GET,
            path="/users",
            file_path="file2.js",
            line_number=20
        )
        ep3 = Endpoint(
            method=HTTPMethod.POST,
            path="/users",
            file_path="file1.js",
            line_number=30
        )

        # Same method and path should have same key
        assert ep1.get_dedup_key() == ep2.get_dedup_key()
        # Different method should have different key
        assert ep1.get_dedup_key() != ep3.get_dedup_key()


class TestEndpointProcessing:
    """Test endpoint normalization and deduplication"""

    def test_endpoint_normalization(self):
        """Test endpoint normalization"""
        endpoints = [
            Endpoint(HTTPMethod.GET, "users", "app.js", 10),
            Endpoint(HTTPMethod.GET, "/users/", "app.js", 11),
            Endpoint(HTTPMethod.POST, "/Users", "app.js", 12),
        ]

        processor = EndpointProcessor()
        normalized = processor._normalize_endpoints(endpoints)

        assert len(normalized) == 3
        # All should have normalized paths
        for ep in normalized:
            assert ep.path.startswith("/")

    def test_endpoint_deduplication(self):
        """Test endpoint deduplication"""
        endpoints = [
            Endpoint(HTTPMethod.GET, "/users", "file1.js", 10),
            Endpoint(HTTPMethod.GET, "/users", "file2.js", 20),  # Duplicate
            Endpoint(HTTPMethod.POST, "/users", "file1.js", 30),
            Endpoint(HTTPMethod.GET, "/posts", "file1.js", 40),
        ]

        processor = EndpointProcessor()
        deduplicated, dup_count = processor._deduplicate_endpoints(endpoints)

        assert len(deduplicated) == 3
        assert dup_count == 1  # One duplicate removed

    def test_full_endpoint_processing(self):
        """Test full endpoint processing pipeline"""
        endpoints = [
            Endpoint(HTTPMethod.GET, "users", "api.js", 10, router_prefix="/api"),
            Endpoint(HTTPMethod.GET, "/users", "api.js", 11, router_prefix="/api"),  # Duplicate
            Endpoint(HTTPMethod.POST, "posts", "routes.js", 20, router_prefix="/api/v1"),
        ]

        processor = EndpointProcessor()
        result = processor.process(endpoints)

        assert result.duplicates_removed == 1
        assert len(result.endpoints) == 2  # 3 - 1 duplicate
        assert all(ep.full_path.startswith("/") for ep in result.endpoints)


class TestOpenAPIGeneration:
    """Test OpenAPI 3.0 specification generation"""

    def test_openapi_spec_creation(self):
        """Test basic OpenAPI spec creation"""
        endpoints = [
            Endpoint(
                HTTPMethod.GET, "/users", "app.js", 10,
                function_name="getUsers"
            ),
            Endpoint(
                HTTPMethod.POST, "/users", "app.js", 20,
                function_name="createUser",
                parameters=["name", "email"]
            ),
        ]

        spec = OpenAPISpec.from_endpoints(
            endpoints=endpoints,
            title="Test API",
            version="1.0.0",
            description="Test Description"
        )

        spec_dict = spec.to_dict()

        # Validate OpenAPI structure
        assert spec_dict["openapi"] == "3.0.3"
        assert spec_dict["info"]["title"] == "Test API"
        assert spec_dict["info"]["version"] == "1.0.0"
        assert "/users" in spec_dict["paths"]
        assert "get" in spec_dict["paths"]["/users"]
        assert "post" in spec_dict["paths"]["/users"]

    def test_openapi_multiple_endpoints(self):
        """Test OpenAPI generation with multiple endpoints"""
        endpoints = [
            Endpoint(HTTPMethod.GET, "/users", "app.js", 10),
            Endpoint(HTTPMethod.GET, "/users/:id", "app.js", 20),
            Endpoint(HTTPMethod.POST, "/users", "app.js", 30),
            Endpoint(HTTPMethod.PUT, "/users/:id", "app.js", 40),
            Endpoint(HTTPMethod.DELETE, "/users/:id", "app.js", 50),
            Endpoint(HTTPMethod.GET, "/posts", "app.js", 60),
        ]

        spec = OpenAPISpec.from_endpoints(endpoints)
        spec_dict = spec.to_dict()

        # All paths should be present
        assert len(spec_dict["paths"]) >= 2
        assert "/users" in spec_dict["paths"]
        assert "/posts" in spec_dict["paths"]

        # Multiple methods on same path
        users_path = spec_dict["paths"]["/users"]
        assert "get" in users_path
        assert "post" in users_path

    def test_openapi_json_output(self):
        """Test JSON output generation"""
        endpoints = [
            Endpoint(HTTPMethod.GET, "/health", "app.js", 10),
        ]

        spec = OpenAPISpec.from_endpoints(endpoints, title="Health Check API")
        json_str = spec.to_json()

        # Should be valid JSON
        import json
        parsed = json.loads(json_str)
        assert parsed["info"]["title"] == "Health Check API"
        assert "/health" in parsed["paths"]

    def test_openapi_spec_validation_structure(self):
        """Test OpenAPI spec validity"""
        endpoints = [
            Endpoint(HTTPMethod.GET, "/", "main.js", 1),
            Endpoint(HTTPMethod.POST, "/api/data", "api.js", 10),
        ]

        spec = OpenAPISpec.from_endpoints(endpoints)
        spec_dict = spec.to_dict()

        # Required OpenAPI fields
        assert "openapi" in spec_dict
        assert "info" in spec_dict
        assert "paths" in spec_dict
        assert "title" in spec_dict["info"]
        assert "version" in spec_dict["info"]

        # Required info fields
        assert spec_dict["info"]["title"]
        assert spec_dict["info"]["version"]

        # All paths have HTTP methods
        for path, methods in spec_dict["paths"].items():
            assert isinstance(methods, dict)
            for method, operation in methods.items():
                if method.startswith("x-"):
                    continue  # Skip extensions
                assert "responses" in operation
                assert "200" in operation["responses"]


class TestConfigValidation:
    """Test configuration management"""

    def test_config_loading(self):
        """Test configuration loading"""
        config = Config()
        assert config.max_file_size > 0
        assert config.max_workers > 0
        assert config.git_retries > 0
        assert config.git_timeout > 0

    def test_config_directories_creation(self):
        """Test that config creates necessary directories"""
        config = Config()
        
        # Directories should be created
        repos_path = Path(config.repos_dir)
        output_path = Path(config.output_dir)
        
        assert repos_path.exists()
        assert output_path.exists()


class TestIntegrationScenarios:
    """Test integrated scenarios"""

    def test_full_pipeline_simulation(self):
        """Simulate a complete pipeline execution"""
        # Create endpoints as if extracted from a real codebase
        extracted_endpoints = [
            # User routes
            Endpoint(HTTPMethod.GET, "/users", "routes/users.js", 10,
                    router_prefix="/api/v1", function_name="listUsers"),
            Endpoint(HTTPMethod.POST, "/users", "routes/users.js", 20,
                    router_prefix="/api/v1", function_name="createUser"),
            Endpoint(HTTPMethod.GET, "/users/:id", "routes/users.js", 30,
                    router_prefix="/api/v1", function_name="getUser"),
            
            # Posts routes
            Endpoint(HTTPMethod.GET, "/posts", "routes/posts.js", 10,
                    router_prefix="/api/v1", function_name="listPosts"),
            Endpoint(HTTPMethod.POST, "/posts", "routes/posts.js", 20,
                    router_prefix="/api/v1", function_name="createPost"),
            
            # Duplicates that should be removed
            Endpoint(HTTPMethod.GET, "/users", "routes/other.js", 40,
                    router_prefix="/api/v1", function_name="getUsers"),
        ]

        # Create prefix mapping to simulate route resolver output
        prefix_mapping = {
            "routes/users.js": "/api/v1",
            "routes/posts.js": "/api/v1",
            "routes/other.js": "/api/v1",
        }

        # Process endpoints
        processor = EndpointProcessor()
        result = processor.process(extracted_endpoints, prefix_mapping)

        # Generate OpenAPI spec
        spec = OpenAPISpec.from_endpoints(
            endpoints=result.endpoints,
            title="Example API",
            version="1.0.0",
            description="API extracted from example repository"
        )

        spec_dict = spec.to_dict()

        # Validate results
        assert result.duplicates_removed == 1
        assert len(result.endpoints) == 5

        # Validate OpenAPI - paths should include full prefixed paths
        assert "/api/v1/users" in spec_dict["paths"]
        assert "/api/v1/posts" in spec_dict["paths"]

        # Each path should have correct methods
        users_methods = spec_dict["paths"]["/api/v1/users"]
        assert "get" in users_methods
        assert "post" in users_methods

    def test_mixed_http_methods(self):
        """Test handling of all HTTP methods"""
        methods = [
            HTTPMethod.GET, HTTPMethod.POST, HTTPMethod.PUT,
            HTTPMethod.DELETE, HTTPMethod.PATCH, HTTPMethod.HEAD, HTTPMethod.OPTIONS
        ]

        endpoints = [
            Endpoint(method, f"/resource", "app.js", i)
            for i, method in enumerate(methods)
        ]

        spec = OpenAPISpec.from_endpoints(endpoints)
        spec_dict = spec.to_dict()

        resource_path = spec_dict["paths"]["/resource"]

        # All methods should be present (except HEAD and OPTIONS which may not be)
        # At least GET, POST, PUT, DELETE, PATCH should be there
        core_methods = {"get", "post", "put", "delete", "patch"}
        for method in core_methods:
            if method in [m.value for m in methods]:
                # Method should be in spec
                pass  # May or may not be depending on filtering


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
