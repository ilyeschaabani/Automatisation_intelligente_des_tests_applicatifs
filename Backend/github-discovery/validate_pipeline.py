"""Production-grade validation for the complete GitHub API Discovery Pipeline"""

import json
from datetime import datetime
from pathlib import Path
from src.models.endpoint import Endpoint, HTTPMethod
from src.endpoint_processor import EndpointProcessor
from src.models.openapi import OpenAPISpec
from src.config import Config


def validate_endpoint_coverage():
    """Validate all endpoint types are correctly detected"""
    print("[VALIDATION] Testing endpoint coverage...")
    
    endpoints = [
        # Express routes
        Endpoint(HTTPMethod.GET, "/api/users", "routes/users.js", 10, "/api/v1", function_name="listUsers"),
        Endpoint(HTTPMethod.POST, "/api/users", "routes/users.js", 20, "/api/v1", function_name="createUser"),
        
        # Flask routes
        Endpoint(HTTPMethod.GET, "/api/posts", "routes/posts.py", 5, "/api/v1", function_name="get_posts"),
        Endpoint(HTTPMethod.PUT, "/api/posts/:id", "routes/posts.py", 15, "/api/v1", function_name="update_post"),
        
        # Spring Boot routes
        Endpoint(HTTPMethod.DELETE, "/api/products", "ProductController.java", 25, "/api/v1", function_name="deleteProduct"),
        
        # GraphQL
        Endpoint(HTTPMethod.POST, "/graphql/schema", "graphql/schema.js", 70, None, function_name="graphql_handler"),
        
        # General endpoint
        Endpoint(HTTPMethod.OPTIONS, "/api/preflight", "middleware.js", 12, "/api/v1", function_name="handlePreflight"),
    ]
    
    processor = EndpointProcessor()
    result = processor.process(endpoints)
    
    assert len(result.endpoints) == len(endpoints), "Endpoint count mismatch"
    assert all(ep.full_path for ep in result.endpoints), "All endpoints should have full_path"
    
    print(f"  [OK] {len(result.endpoints)} endpoints correctly processed")
    return True


def validate_prefix_resolution():
    """Validate router prefix resolution works correctly"""
    print("[VALIDATION] Testing prefix resolution...")
    
    endpoints = [
        Endpoint(HTTPMethod.GET, "/list", "admin.js", 10, None, function_name="listAdmins"),
        Endpoint(HTTPMethod.POST, "/add", "admin.js", 20, None, function_name="addAdmin"),
        Endpoint(HTTPMethod.GET, "/list", "users.js", 5, None, function_name="listUsers"),
        Endpoint(HTTPMethod.POST, "/add", "users.js", 15, None, function_name="addUser"),
    ]
    
    prefix_mapping = {
        "admin.js": "/admin",
        "users.js": "/api/users"
    }
    
    processor = EndpointProcessor()
    result = processor.process(endpoints, router_prefixes=prefix_mapping)
    
    paths = {(ep.method, ep.full_path) for ep in result.endpoints}
    
    assert (HTTPMethod.GET, "/admin/list") in paths, "Admin prefix not resolved"
    assert (HTTPMethod.GET, "/api/users/list") in paths, "Users API prefix not resolved"
    
    print(f"  [OK] Prefixes correctly resolved to {len(paths)} unique paths")
    return True


def validate_deduplication():
    """Validate that duplicate endpoints are correctly removed"""
    print("[VALIDATION] Testing deduplication...")
    
    endpoints = [
        Endpoint(HTTPMethod.GET, "/users", "file1.js", 10, "/api", function_name="getUsers1"),
        Endpoint(HTTPMethod.GET, "/users", "file2.js", 5, "/api", function_name="getUsers2"),  # Duplicate
        Endpoint(HTTPMethod.POST, "/users", "file1.js", 20, "/api", function_name="createUser1"),
        Endpoint(HTTPMethod.POST, "/users", "file3.py", 15, "/api", function_name="createUser2"),  # Duplicate
    ]
    
    processor = EndpointProcessor()
    result = processor.process(endpoints)
    
    assert result.duplicates_removed == 2, f"Expected 2 duplicates removed, got {result.duplicates_removed}"
    assert len(result.endpoints) == 2, f"Expected 2 unique endpoints, got {len(result.endpoints)}"
    
    print(f"  [OK] {result.duplicates_removed} duplicates removed from {len(endpoints)} endpoints")
    return True


def validate_openapi_generation():
    """Validate OpenAPI spec generation is compliant"""
    print("[VALIDATION] Testing OpenAPI generation...")
    
    endpoints = [
        Endpoint(HTTPMethod.GET, "/users", "users.js", 10, None, function_name="getUsers"),
        Endpoint(HTTPMethod.GET, "/users/:id", "users.js", 20, None, function_name="getUser"),
        Endpoint(HTTPMethod.POST, "/users", "users.js", 30, None, function_name="createUser"),
        Endpoint(HTTPMethod.PUT, "/users/:id", "users.js", 40, None, function_name="updateUser"),
        Endpoint(HTTPMethod.DELETE, "/users/:id", "users.js", 50, None, function_name="deleteUser"),
    ]
    
    spec = OpenAPISpec.from_endpoints(endpoints, title="Test API", version="1.0.0")
    spec_dict = spec.to_dict()
    
    # Validate OpenAPI structure
    assert spec_dict["openapi"] == "3.0.3", "OpenAPI version should be 3.0.3"
    assert "info" in spec_dict, "Missing info section"
    assert "paths" in spec_dict, "Missing paths section"
    
    # Check for paths - they should be normalized
    paths = spec_dict["paths"]
    assert len(paths) > 0, "No paths found in OpenAPI spec"
    
    # Validate path normalization (should have {id} not :id)
    path_list = list(paths.keys())
    assert any("{" in p for p in path_list), "Parameterized paths not properly normalized to OpenAPI format"
    
    # Validate methods exist
    for path in path_list:
        if "{id}" in path:
            # Parameterized endpoints should have GET, PUT, DELETE
            assert "get" in paths[path] or "put" in paths[path] or "delete" in paths[path], f"No methods found for parameterized path {path}"
    
    print(f"  [OK] Valid OpenAPI 3.0.3 spec with {len(paths)} paths")
    return True


def validate_path_normalization():
    """Validate path normalization handles edge cases"""
    print("[VALIDATION] Testing path normalization...")
    
    test_cases = [
        ("/users", "/users"),           # Already normalized
        ("users", "/users"),             # Add leading slash
        ("/users/", "/users"),           # Remove trailing slash
        ("//users///", "/users"),        # Handle double slashes
        ("/users?query=1", "/users"),    # Remove query string
    ]
    
    processor = EndpointProcessor()
    
    for input_path, expected in test_cases:
        result = processor._normalize_path(input_path)
        assert result == expected, f"Path normalization failed: {input_path} -> {result} (expected {expected})"
    
    print(f"  [OK] {len(test_cases)} path normalization cases passed")
    return True


def validate_http_methods():
    """Validate all HTTP methods are supported"""
    print("[VALIDATION] Testing HTTP method support...")
    
    methods = [
        HTTPMethod.GET,
        HTTPMethod.POST,
        HTTPMethod.PUT,
        HTTPMethod.DELETE,
        HTTPMethod.PATCH,
        HTTPMethod.HEAD,
        HTTPMethod.OPTIONS,
    ]
    
    endpoints = [
        Endpoint(method, f"/endpoint{i}", "file.js", i*10, None, function_name=f"handler{i}")
        for i, method in enumerate(methods)
    ]
    
    processor = EndpointProcessor()
    result = processor.process(endpoints)
    
    assert len(result.endpoints) == len(methods), f"Expected {len(methods)} methods, got {len(result.endpoints)}"
    
    print(f"  [OK] All {len(methods)} HTTP methods supported")
    return True


def validate_config():
    """Validate configuration loading and directory creation"""
    print("[VALIDATION] Testing configuration...")
    
    config = Config()
    
    # Check settings
    assert config.max_file_size > 0, "Invalid max_file_size"
    assert config.max_workers > 0, "Invalid max_workers"
    assert config.cache_ttl > 0, "Invalid cache_ttl"
    
    # Check directories
    assert config.output_dir is not None, "Output directory not configured"
    assert config.repos_dir is not None, "Repos directory not configured"
    
    print(f"  [OK] Configuration validated: output_dir={config.output_dir}, repos_dir={config.repos_dir}, max_workers={config.max_workers}")
    return True


def validate_complex_scenario():
    """Validate a complex real-world scenario with multiple sources"""
    print("[VALIDATION] Testing complex scenario (10 endpoints with 2 duplicates)...")
    
    endpoints = [
        # Express backend
        Endpoint(HTTPMethod.GET, "/users", "backend/express/routes.js", 10, "/api/v1", function_name="getUsers"),
        Endpoint(HTTPMethod.POST, "/users", "backend/express/routes.js", 20, "/api/v1", function_name="createUser"),
        Endpoint(HTTPMethod.GET, "/users/:id", "backend/express/routes.js", 30, "/api/v1", function_name="getUser"),
        
        # Flask backend  
        Endpoint(HTTPMethod.GET, "/products", "backend/flask/routes.py", 5, "/api/v1", function_name="get_products"),
        Endpoint(HTTPMethod.POST, "/products", "backend/flask/routes.py", 15, "/api/v1", function_name="create_product"),
        Endpoint(HTTPMethod.GET, "/products/:id", "backend/flask/routes.py", 25, "/api/v1", function_name="get_product"),
        
        # Duplicates from different sources
        Endpoint(HTTPMethod.GET, "/users", "frontend/express/api.js", 10, "/api/v1", function_name="getUsers_duplicate"),
        Endpoint(HTTPMethod.GET, "/products", "frontend/flask/api.py", 5, "/api/v1", function_name="get_products_duplicate"),
        
        # Admin routes
        Endpoint(HTTPMethod.GET, "/admin/stats", "admin.js", 50, "/admin", function_name="getStats"),
        Endpoint(HTTPMethod.POST, "/admin/settings", "admin.js", 60, "/admin", function_name="updateSettings"),
    ]
    
    processor = EndpointProcessor()
    result = processor.process(endpoints)
    
    # Should have 8 unique endpoints after removing 2 duplicates
    assert len(result.endpoints) == 8, f"Expected 8 unique endpoints, got {len(result.endpoints)}"
    assert result.duplicates_removed == 2, f"Expected 2 duplicates, got {result.duplicates_removed}"
    
    # Verify expected paths exist
    paths = {ep.full_path for ep in result.endpoints}
    assert "/api/v1/users" in paths, "Missing /api/v1/users"
    assert "/api/v1/products" in paths, "Missing /api/v1/products"
    assert "/admin/admin/stats" in paths, "Missing /admin/admin/stats"
    
    print(f"  [OK] Complex scenario validated: {len(result.endpoints)} unique endpoints from {len(endpoints)} originals")
    return True


def main():
    """Run all validation tests"""
    print("=" * 70)
    print("PRODUCTION PIPELINE VALIDATION")
    print(f"Timestamp: {datetime.now().isoformat()}")
    print("=" * 70)
    print()
    
    validations = [
        validate_endpoint_coverage,
        validate_prefix_resolution,
        validate_deduplication,
        validate_openapi_generation,
        validate_path_normalization,
        validate_http_methods,
        validate_config,
        validate_complex_scenario,
    ]
    
    passed = 0
    failed = 0
    
    for validation_func in validations:
        try:
            if validation_func():
                passed += 1
        except AssertionError as e:
            print(f"  [FAIL] {str(e)}")
            failed += 1
        except Exception as e:
            print(f"  [FAIL] Unexpected error: {str(e)}")
            failed += 1
        print()
    
    # Summary
    print("=" * 70)
    print(f"VALIDATION RESULTS: {passed}/{len(validations)} passed")
    if failed > 0:
        print(f"WARNING: {failed} validation(s) failed!")
    else:
        print("SUCCESS: All validations passed!")
    print("=" * 70)
    
    return failed == 0


if __name__ == "__main__":
    success = main()
    exit(0 if success else 1)
