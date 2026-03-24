"""Endpoint data model"""

from enum import Enum
from dataclasses import dataclass, field
from typing import Optional, List, Set
from pydantic import BaseModel, Field, field_validator


class HTTPMethod(str, Enum):
    """HTTP methods"""
    GET = "get"
    POST = "post"
    PUT = "put"
    DELETE = "delete"
    PATCH = "patch"
    HEAD = "head"
    OPTIONS = "options"


class EndpointModel(BaseModel):
    """Pydantic model for endpoint validation"""
    method: HTTPMethod
    path: str
    file_path: str
    line_number: int
    router_prefix: Optional[str] = None
    full_path: str
    function_name: Optional[str] = None
    parameters: List[str] = Field(default_factory=list)
    middleware: List[str] = Field(default_factory=list)
    confidence: float = Field(default=1.0, ge=0.0, le=1.0)
    source: str = Field(..., description="Source of extraction: 'ast' or 'llm'")
    metadata: dict = Field(default_factory=dict)

    @field_validator("path")
    @classmethod
    def normalize_path(cls, v: str) -> str:
        """Normalize path to start with /"""
        if not v.startswith("/"):
            v = "/" + v
        return v

    @field_validator("full_path")
    @classmethod
    def normalize_full_path(cls, v: str) -> str:
        """Normalize full path to start with /"""
        if not v.startswith("/"):
            v = "/" + v
        return v


@dataclass
class Endpoint:
    """Endpoint representation"""
    method: HTTPMethod
    path: str
    file_path: str
    line_number: int
    router_prefix: Optional[str] = None
    full_path: str = ""
    function_name: Optional[str] = None
    parameters: List[str] = field(default_factory=list)
    middleware: List[str] = field(default_factory=list)
    confidence: float = 1.0
    source: str = "ast"  # 'ast' or 'llm'
    metadata: dict = field(default_factory=dict)

    def __post_init__(self):
        """Normalize paths and compute full_path if not provided"""
        if not self.path.startswith("/"):
            self.path = "/" + self.path

        if not self.full_path:
            if self.router_prefix:
                self.full_path = self._combine_paths(self.router_prefix, self.path)
            else:
                self.full_path = self.path

        if not self.full_path.startswith("/"):
            self.full_path = "/" + self.full_path

    def _combine_paths(self, prefix: str, path: str) -> str:
        """Combine prefix and path intelligently"""
        # Remove trailing slash from prefix
        prefix = prefix.rstrip("/")
        # Remove leading slash from path
        path = path.lstrip("/")
        # Combine
        if prefix and path:
            return f"{prefix}/{path}"
        elif prefix:
            return prefix
        else:
            return path

    def to_dict(self) -> dict:
        """Convert to dictionary"""
        return {
            "method": self.method.value,
            "path": self.path,
            "file_path": self.file_path,
            "line_number": self.line_number,
            "router_prefix": self.router_prefix,
            "full_path": self.full_path,
            "function_name": self.function_name,
            "parameters": self.parameters,
            "middleware": self.middleware,
            "confidence": self.confidence,
            "source": self.source,
            "metadata": self.metadata,
        }

    @classmethod
    def from_dict(cls, data: dict) -> "Endpoint":
        """Create Endpoint from dictionary"""
        # Convert string method to HTTPMethod enum
        if isinstance(data.get("method"), str):
            data["method"] = HTTPMethod(data["method"].lower())

        return cls(**data)

    def get_dedup_key(self) -> tuple:
        """Get key for deduplication (method + full_path)"""
        return (self.method, self.full_path)