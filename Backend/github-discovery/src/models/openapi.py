"""OpenAPI 3.0 specification generator"""

from typing import Dict, List, Any, Optional
from dataclasses import dataclass, field, asdict
from datetime import datetime, timezone
import json

from .endpoint import Endpoint, HTTPMethod


@dataclass
class OpenAPISpec:
    """OpenAPI 3.0 specification builder"""

    title: str = "API Specification"
    version: str = "1.0.0"
    description: str = ""
    servers: List[Dict[str, str]] = field(default_factory=lambda: [{"url": "https://api.example.com"}])
    endpoints: List[Endpoint] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        """Convert to OpenAPI 3.0 JSON dictionary"""
        spec = {
            "openapi": "3.0.3",
            "info": {
                "title": self.title,
                "version": self.version,
                "description": self.description,
                "x-generated": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
                "x-endpoint-count": len(self.endpoints),
            },
            "servers": self.servers,
            "paths": {},
            "components": {
                "securitySchemes": {
                    "bearerAuth": {
                        "type": "http",
                        "scheme": "bearer",
                        "bearerFormat": "JWT",
                    }
                }
            },
            "security": [{"bearerAuth": []}],
            "tags": [],
        }

        # Group endpoints by path
        path_groups: Dict[str, Dict[HTTPMethod, Endpoint]] = {}
        for endpoint in self.endpoints:
            # Normalize path: convert :param to {param} for OpenAPI compliance
            normalized_path = self._normalize_path_to_openapi(endpoint.full_path)
            if normalized_path not in path_groups:
                path_groups[normalized_path] = {}
            path_groups[normalized_path][endpoint.method] = endpoint

        # Build paths object
        for path, methods_dict in path_groups.items():
            spec["paths"][path] = {}
            for method, endpoint in methods_dict.items():
                operation = {
                    "summary": endpoint.function_name or f"{method.upper()} {path}",
                    "tags": self._extract_tags(endpoint),
                    "responses": {
                        "200": {
                            "description": "Success",
                            "content": {
                                "application/json": {
                                    "schema": {"type": "object"}
                                }
                            }
                        },
                        "400": {
                            "description": "Bad Request"
                        },
                        "401": {
                            "description": "Unauthorized"
                        },
                        "403": {
                            "description": "Forbidden"
                        },
                        "404": {
                            "description": "Not Found"
                        },
                        "500": {
                            "description": "Internal Server Error"
                        }
                    }
                }

                # Add parameters if detected
                if endpoint.parameters:
                    operation["parameters"] = self._build_parameters(endpoint.parameters, path)

                # Add metadata as extensions
                if endpoint.metadata:
                    operation["x-metadata"] = endpoint.metadata

                # Add confidence score
                if endpoint.confidence < 1.0:
                    operation["x-confidence"] = endpoint.confidence

                # Add source
                operation["x-source"] = endpoint.source

                spec["paths"][path][method.value] = operation

        # Build unique tags
        all_tags = set()
        for endpoint in self.endpoints:
            tags = self._extract_tags(endpoint)
            all_tags.update(tags)

        spec["tags"] = [{"name": tag} for tag in sorted(all_tags)]

        return spec

    def _normalize_path_to_openapi(self, path: str) -> str:
        """
        Convert path parameters from various formats to OpenAPI format.
        
        Examples:
            /users/:id -> /users/{id}
            /posts/{postId} -> /posts/{postId} (already correct)
            /articles/*slug -> /articles/{slug}
        """
        import re
        
        # Convert :param to {param}
        path = re.sub(r':(\w+)', r'{\1}', path)
        
        # Convert *param to {param}
        path = re.sub(r'\*(\w+)', r'{\1}', path)
        
        return path

    def _extract_tags(self, endpoint: Endpoint) -> List[str]:
        """Extract tags from endpoint for grouping"""
        import os
        import re
        
        tags = []

        # Extract meaningful name from file path
        if endpoint.file_path:
            # Get filename without extension
            filename = os.path.basename(endpoint.file_path)
            name_without_ext = os.path.splitext(filename)[0]
            
            # Try to extract meaningful controller/module name
            # e.g., "user_controller" -> "user"
            # e.g., "UserController" -> "user"
            # e.g., "users" -> "users"
            
            # Remove common suffixes
            for suffix in ["_controller", "controller", "_routes", "routes", "_service", "service"]:
                if name_without_ext.lower().endswith(suffix):
                    name_without_ext = name_without_ext[:-len(suffix)]
            
            if name_without_ext:
                # Convert camelCase/PascalCase to lowercase
                # e.g., "User" -> "user", "userController" -> "user_controller"
                name_tag = re.sub(r'([A-Z])', r'_\1', name_without_ext).lower().strip('_')
                if name_tag:
                    tags.append(name_tag)

        # Use function name prefix as tag
        if endpoint.function_name:
            # Convert camelCase to words and take first word
            name = endpoint.function_name
            # Simple split on capitals
            words = re.findall(r'[A-Z][a-z]*|[a-z]+', name)
            if words:
                tags.append(words[0].lower())

        # Default tag if nothing else worked
        if not tags:
            tags.append("api")

        # Remove duplicates and limit to 3
        return list(set(tags))[:3]

    def _build_parameters(self, parameters: List[str], path: str = None) -> List[Dict[str, Any]]:
        """Build OpenAPI parameter objects"""
        import re
        
        param_objects = []
        
        # If path is provided, extract path parameters from it
        if path:
            # Extract parameters from {param} notation
            path_params = re.findall(r'\{(\w+)\}', path)
            for param in path_params:
                param_obj = {
                    "name": param,
                    "in": "path",
                    "required": True,
                    "schema": {
                        "type": "string"
                    }
                }
                param_objects.append(param_obj)
        else:
            # Build from parameters list
            for param in parameters:
                # Parse parameter format: "name:type" or just "name"
                if ":" in param:
                    name, param_type = param.split(":", 1)
                else:
                    name, param_type = param, "string"

                param_obj = {
                    "name": name,
                    "in": "path",  # Assume path parameters
                    "required": True,
                    "schema": {
                        "type": self._map_type(param_type)
                    }
                }
                param_objects.append(param_obj)

        return param_objects

    def _map_type(self, param_type: str) -> str:
        """Map custom type to OpenAPI schema type"""
        type_mapping = {
            "int": "integer",
            "integer": "integer",
            "string": "string",
            "bool": "boolean",
            "boolean": "boolean",
            "number": "number",
            "float": "number",
            "uuid": "string",
            "id": "string",
        }
        return type_mapping.get(param_type.lower(), "string")

    def to_json(self, indent: int = 2) -> str:
        """Convert to JSON string"""
        return json.dumps(self.to_dict(), indent=indent, ensure_ascii=False)

    def save(self, filepath: str) -> None:
        """Save to file"""
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(self.to_json())

    @classmethod
    def from_endpoints(cls, endpoints: List[Endpoint], title: str = "API Specification", version: str = "1.0.0", description: str = "") -> "OpenAPISpec":
        """Create OpenAPI spec from list of endpoints"""
        spec = cls(title=title, version=version, description=description)
        spec.endpoints = endpoints
        return spec