"""Configuration management with environment variables"""

import os
from dataclasses import dataclass
from typing import Optional
from dotenv import load_dotenv

load_dotenv()


@dataclass
class Config:
    """Application configuration"""

    # OpenRouter (preferred for LLM with multiple model options)
    openrouter_api_key: Optional[str] = os.getenv("OPENROUTER_API_KEY")
    openrouter_model: str = os.getenv("OPENROUTER_MODEL", "openai/gpt-4o-mini")
    openrouter_base_url: str = os.getenv("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1")

    # Legacy OpenAI support
    openai_api_key: Optional[str] = os.getenv("OPENAI_API_KEY")
    llm_model: str = os.getenv("LLM_MODEL", "gpt-4")

    # Ollama support (local LLM, optional)
    ollama_enabled: bool = os.getenv("OLLAMA_ENABLED", "").strip().lower() in {"1", "true", "yes", "y"}
    ollama_base_url: str = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434").rstrip("/")
    ollama_model: str = os.getenv("OLLAMA_MODEL", "llama3.1")
    ollama_timeout_seconds: int = int(os.getenv("OLLAMA_TIMEOUT_SECONDS", "60"))

    # LLM Configuration
    llm_confidence_threshold: float = float(os.getenv("LLM_CONFIDENCE_THRESHOLD", "0.75"))

    # Processing
    max_file_size: int = int(os.getenv("MAX_FILE_SIZE", "100000"))
    max_workers: int = int(os.getenv("MAX_WORKERS", "4"))
    cache_ttl: int = int(os.getenv("CACHE_TTL", "3600"))

    # AST / Tree-sitter
    ast_enabled: bool = os.getenv("AST_ENABLED", "true").strip().lower() in {"1", "true", "yes", "y"}
    ast_allow_language_pack_download: bool = (
        os.getenv("AST_ALLOW_LANGUAGE_PACK_DOWNLOAD", "true").strip().lower() in {"1", "true", "yes", "y"}
    )
    ast_disabled_languages: str = os.getenv("AST_DISABLED_LANGUAGES", "").strip()

    # Paths
    repos_dir: str = "repos"
    output_dir: str = "output"

    # Logging
    log_level: str = os.getenv("LOG_LEVEL", "INFO")

    # Outputs
    output_openapi: bool = os.getenv("OUTPUT_OPENAPI", "false").strip().lower() in {"1", "true", "yes", "y"}

    # Git
    git_timeout: int = 300
    git_retries: int = 3

    def __post_init__(self):
        """Create necessary directories"""
        os.makedirs(self.repos_dir, exist_ok=True)
        os.makedirs(self.output_dir, exist_ok=True)