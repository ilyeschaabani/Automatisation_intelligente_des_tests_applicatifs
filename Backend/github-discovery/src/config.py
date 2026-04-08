"""Configuration management with environment variables"""

import os
from dataclasses import dataclass, field
from typing import Optional
from dotenv import load_dotenv

load_dotenv()


def _getenv_bool(name: str, default: bool) -> bool:
    v = os.getenv(name)
    if v is None:
        return default
    return v.strip().lower() in {"1", "true", "yes", "y", "on"}


def _getenv_int(name: str, default: int) -> int:
    v = os.getenv(name)
    if v is None or v.strip() == "":
        return default
    try:
        return int(v)
    except Exception:
        return default


def _getenv_float(name: str, default: float) -> float:
    v = os.getenv(name)
    if v is None or v.strip() == "":
        return default
    try:
        return float(v)
    except Exception:
        return default


@dataclass
class Config:
    """Application configuration"""

    # LLM Provider selection
    # - auto (default): prefer LLM_BASE_URL, then OPENROUTER_API_KEY, then OPENAI_API_KEY
    # - local: OpenAI-compatible server at LLM_BASE_URL (Ollama/vLLM/TGI/etc.)
    # - openrouter: use OpenRouter only
    # - openai: use OpenAI only
    # - none: disable LLM
    llm_provider: str = field(default_factory=lambda: os.getenv("LLM_PROVIDER", "auto"))
    llm_base_url: Optional[str] = field(default_factory=lambda: os.getenv("LLM_BASE_URL"))
    llm_api_key: Optional[str] = field(default_factory=lambda: os.getenv("LLM_API_KEY"))

    # Debug/testing: force Stage 3 (LLM) to run even if no low-confidence endpoints exist.
    llm_force: bool = field(default_factory=lambda: _getenv_bool("LLM_FORCE", False))
    llm_force_max_files: int = field(default_factory=lambda: _getenv_int("LLM_FORCE_MAX_FILES", 3))

    # Final-stage LLM verification of request templates (for test generation readiness)
    # - When enabled, the pipeline asks the LLM to validate that request templates are complete
    #   (path/query/header/body schemas + examples) and returns safe corrections.
    # - Strict mode fails the run if required testing fields are still missing after auto-fixes.
    llm_verify: bool = field(default_factory=lambda: _getenv_bool("LLM_VERIFY", False))
    llm_verify_strict: bool = field(default_factory=lambda: _getenv_bool("LLM_VERIFY_STRICT", False))
    llm_verify_max_endpoints: int = field(default_factory=lambda: _getenv_int("LLM_VERIFY_MAX_ENDPOINTS", 40))
    llm_verify_chunk_size: int = field(default_factory=lambda: _getenv_int("LLM_VERIFY_CHUNK_SIZE", 15))

    # OpenRouter (preferred for LLM with multiple model options)
    openrouter_api_key: Optional[str] = field(default_factory=lambda: os.getenv("OPENROUTER_API_KEY"))
    openrouter_model: str = field(default_factory=lambda: os.getenv("OPENROUTER_MODEL", "openai/gpt-4o-mini"))
    openrouter_base_url: str = field(default_factory=lambda: os.getenv("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1"))

    # Legacy OpenAI support
    openai_api_key: Optional[str] = field(default_factory=lambda: os.getenv("OPENAI_API_KEY"))
    llm_model: str = field(default_factory=lambda: os.getenv("LLM_MODEL", "gpt-4"))

    # LLM Configuration
    llm_confidence_threshold: float = field(default_factory=lambda: _getenv_float("LLM_CONFIDENCE_THRESHOLD", 0.75))

    # Processing
    max_file_size: int = field(default_factory=lambda: _getenv_int("MAX_FILE_SIZE", 100000))
    max_workers: int = field(default_factory=lambda: _getenv_int("MAX_WORKERS", 4))
    cache_ttl: int = field(default_factory=lambda: _getenv_int("CACHE_TTL", 3600))

    # Paths
    # Allow overriding from environment to support Windows path-length mitigation.
    # Example: set REPOS_DIR=C:\\r to shorten checkout paths.
    repos_dir: str = field(default_factory=lambda: os.getenv("REPOS_DIR", "repos"))
    output_dir: str = field(default_factory=lambda: os.getenv("OUTPUT_DIR", "output"))

    # Logging
    log_level: str = field(default_factory=lambda: os.getenv("LOG_LEVEL", "INFO"))

    # Git
    git_timeout: int = 300
    git_retries: int = field(default_factory=lambda: _getenv_int("GIT_RETRIES", 3))

    # Docker / Environment runner (disabled by default for safety)
    allow_docker: bool = field(default_factory=lambda: _getenv_bool("DISCOVERY_ALLOW_DOCKER", False))
    docker_compose_command: str = field(default_factory=lambda: os.getenv("DISCOVERY_DOCKER_COMPOSE_COMMAND", "docker"))
    docker_compose_subcommand: str = field(default_factory=lambda: os.getenv("DISCOVERY_DOCKER_COMPOSE_SUBCOMMAND", "compose"))
    docker_compose_project_prefix: str = field(default_factory=lambda: os.getenv("DISCOVERY_DOCKER_PROJECT_PREFIX", "gd"))

    def __post_init__(self):
        """Create necessary directories"""
        os.makedirs(self.repos_dir, exist_ok=True)
        os.makedirs(self.output_dir, exist_ok=True)