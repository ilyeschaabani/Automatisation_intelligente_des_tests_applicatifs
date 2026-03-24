"""Configuration management with environment variables"""

import os
from dataclasses import dataclass
from typing import Optional
from dotenv import load_dotenv

load_dotenv()


@dataclass
class Config:
    """Application configuration"""

    # LLM Provider selection
    # - auto (default): prefer LLM_BASE_URL, then OPENROUTER_API_KEY, then OPENAI_API_KEY
    # - local: OpenAI-compatible server at LLM_BASE_URL (Ollama/vLLM/TGI/etc.)
    # - openrouter: use OpenRouter only
    # - openai: use OpenAI only
    # - none: disable LLM
    llm_provider: str = os.getenv("LLM_PROVIDER", "auto")
    llm_base_url: Optional[str] = os.getenv("LLM_BASE_URL")
    llm_api_key: Optional[str] = os.getenv("LLM_API_KEY")

    # Debug/testing: force Stage 3 (LLM) to run even if no low-confidence endpoints exist.
    llm_force: bool = os.getenv("LLM_FORCE", "false").strip().lower() in {"1", "true", "yes", "y", "on"}
    llm_force_max_files: int = int(os.getenv("LLM_FORCE_MAX_FILES", "3"))

    # OpenRouter (preferred for LLM with multiple model options)
    openrouter_api_key: Optional[str] = os.getenv("OPENROUTER_API_KEY")
    openrouter_model: str = os.getenv("OPENROUTER_MODEL", "openai/gpt-4o-mini")
    openrouter_base_url: str = os.getenv("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1")

    # Legacy OpenAI support
    openai_api_key: Optional[str] = os.getenv("OPENAI_API_KEY")
    llm_model: str = os.getenv("LLM_MODEL", "gpt-4")

    # LLM Configuration
    llm_confidence_threshold: float = float(os.getenv("LLM_CONFIDENCE_THRESHOLD", "0.75"))

    # Processing
    max_file_size: int = int(os.getenv("MAX_FILE_SIZE", "100000"))
    max_workers: int = int(os.getenv("MAX_WORKERS", "4"))
    cache_ttl: int = int(os.getenv("CACHE_TTL", "3600"))

    # Paths
    # Allow overriding from environment to support Windows path-length mitigation.
    # Example: set REPOS_DIR=C:\\r to shorten checkout paths.
    repos_dir: str = os.getenv("REPOS_DIR", "repos")
    output_dir: str = os.getenv("OUTPUT_DIR", "output")

    # Logging
    log_level: str = os.getenv("LOG_LEVEL", "INFO")

    # Git
    git_timeout: int = 300
    git_retries: int = 3

    def __post_init__(self):
        """Create necessary directories"""
        os.makedirs(self.repos_dir, exist_ok=True)
        os.makedirs(self.output_dir, exist_ok=True)