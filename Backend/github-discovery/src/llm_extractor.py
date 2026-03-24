"""LLM-based endpoint extraction for complex cases"""

import os
from typing import List, Optional, Dict, Any
from pathlib import Path
from dataclasses import dataclass

try:
    from openai import OpenAI
    OPENAI_AVAILABLE = True
except ImportError:
    OPENAI_AVAILABLE = False

from .models.endpoint import Endpoint, HTTPMethod
from .utils.logger import get_logger
from .utils.errors import LLMError
from .config import Config


@dataclass
class LLMExtractionResult:
    """Result from LLM extraction"""
    endpoints: List[Endpoint]
    confidence: float
    raw_response: str
    errors: List[str] = None

    def __post_init__(self):
        if self.errors is None:
            self.errors = []


class LLMExtractor:
    """LLM-based endpoint extractor using OpenRouter or OpenAI"""

    SYSTEM_PROMPT = """You are an expert API endpoint analyzer. Your task is to extract API endpoints from code snippets.

IMPORTANT: You must respond with ONLY a valid JSON object, no markdown, no explanations.

JSON structure:
{
  "endpoints": [
    {
      "method": "get|post|put|delete|patch",
      "path": "/api/path",
      "function_name": "handlerFunctionName",
      "parameters": ["param1", "param2"],
      "confidence": 0.95
    }
  ]
}

Guidelines:
- Extract ALL HTTP endpoints (REST, GraphQL if detectable)
- Normalize paths to start with /
- Include path parameters (e.g., /users/:id -> /users/{id})
- Use snake_case or camelCase for function names as they appear
- If uncertain, set confidence < 0.8
- If no endpoints found, return empty array
- Do NOT invent endpoints - only extract from provided code
"""

    USER_PROMPT_TEMPLATE = """Analyze this code and extract API endpoints:

File: {file_path}
Router Prefix: {router_prefix}
Code:
```
{code}
```

Extract all endpoints following the specified JSON format."""

    def __init__(self, config: Config):
        self.config = config
        self.logger = get_logger(__name__)
        self.client = None
        self.use_openrouter = False
        self.provider = None

        if not OPENAI_AVAILABLE:
            raise LLMError("OpenAI package not installed. Install with: pip install openai")

        provider = (config.llm_provider or "auto").strip().lower()

        if provider == "auto":
            if config.llm_base_url:
                provider = "local"
            elif config.openrouter_api_key:
                provider = "openrouter"
            elif config.openai_api_key:
                provider = "openai"
            else:
                provider = "none"

        if provider == "local":
            if not config.llm_base_url:
                raise LLMError("LLM_PROVIDER=local requires LLM_BASE_URL (e.g., http://localhost:11434/v1)")
            api_key = config.llm_api_key or "local"
            self.client = OpenAI(api_key=api_key, base_url=config.llm_base_url)
            self.provider = "local"
            self.logger.info(
                "LLM Extractor initialized with local OpenAI-compatible endpoint",
                model=config.llm_model,
                base_url=config.llm_base_url,
            )
        elif provider == "openrouter":
            if config.openrouter_api_key:
                self.client = OpenAI(
                    api_key=config.openrouter_api_key,
                    base_url=config.openrouter_base_url
                )
                self.use_openrouter = True
                self.provider = "openrouter"
                self.logger.info("LLM Extractor initialized with OpenRouter", model=config.openrouter_model)
            else:
                self.logger.warning("OPENROUTER_API_KEY not set - LLM extraction disabled")
        elif provider == "openai":
            if config.openai_api_key:
                self.client = OpenAI(api_key=config.openai_api_key)
                self.provider = "openai"
                self.logger.info("LLM Extractor initialized with OpenAI", model=config.llm_model)
            else:
                self.logger.warning("OPENAI_API_KEY not set - LLM extraction disabled")
        else:
            self.logger.warning("LLM extraction disabled")

        self.cache = None  # Will be set by pipeline if needed

    def extract_endpoints(
        self,
        code: str,
        file_path: Path,
        language: str,
        router_prefix: Optional[str] = None,
        context: Optional[Dict[str, Any]] = None
    ) -> LLMExtractionResult:
        """
        Extract endpoints from code using LLM (OpenRouter or OpenAI).

        Args:
            code: Source code
            file_path: Path to source file
            language: Programming language
            router_prefix: Optional router prefix
            context: Additional context

        Returns:
            LLMExtractionResult with extracted endpoints
        """
        if not self.client:
            raise LLMError("LLM client not initialized - missing API key")

        try:
            # Check cache first
            cache_key = None
            if self.cache:
                cache_key = self._generate_cache_key(code, router_prefix)
                cached = self.cache.get(cache_key)
                if cached:
                    self.logger.debug("LLM cache hit", file=str(file_path))
                    return cached

            # Build prompt
            user_prompt = self.USER_PROMPT_TEMPLATE.format(
                file_path=str(file_path),
                router_prefix=router_prefix or "None",
                code=code[:4000]  # Limit code length
            )

            # Select model
            model = self.config.openrouter_model if self.use_openrouter else self.config.llm_model

            # Call LLM
            response = self.client.chat.completions.create(
                model=model,
                messages=[
                    {"role": "system", "content": self.SYSTEM_PROMPT},
                    {"role": "user", "content": user_prompt}
                ],
                temperature=0.1,
                max_tokens=2000
            )

            raw_response = response.choices[0].message.content
            self.logger.debug("LLM response received", file=str(file_path), response_length=len(raw_response))

            # Parse response
            import json
            try:
                # Extract JSON from response (in case there's extra text)
                json_start = raw_response.find("{")
                json_end = raw_response.rfind("}") + 1
                if json_start != -1 and json_end != -1:
                    json_str = raw_response[json_start:json_end]
                    data = json.loads(json_str)
                else:
                    raise ValueError("No JSON found in response")
            except json.JSONDecodeError as e:
                raise LLMError(f"Invalid JSON from LLM: {str(e)}", details={"response": raw_response})

            # Convert to Endpoint objects
            endpoints = []
            for ep_data in data.get("endpoints", []):
                try:
                    endpoint = Endpoint(
                        method=HTTPMethod(ep_data["method"].lower()),
                        path=ep_data["path"],
                        file_path=str(file_path),
                        line_number=0,  # LLM doesn't provide line numbers
                        router_prefix=router_prefix,
                        function_name=ep_data.get("function_name"),
                        parameters=ep_data.get("parameters", []),
                        confidence=ep_data.get("confidence", 0.5),
                        source="llm",
                        metadata={
                            "llm_model": model,
                            "llm_provider": (self.provider or ("openrouter" if self.use_openrouter else "openai")),
                        }
                    )
                    endpoints.append(endpoint)
                except Exception as e:
                    self.logger.warning("Failed to create endpoint from LLM data", error=str(e), data=ep_data)

            result = LLMExtractionResult(
                endpoints=endpoints,
                confidence=sum(e.confidence for e in endpoints) / len(endpoints) if endpoints else 0.0,
                raw_response=raw_response
            )

            # Cache result
            if self.cache and cache_key:
                self.cache.set(cache_key, result)

            return result

        except Exception as e:
            self.logger.error("LLM extraction failed", file=str(file_path), error=str(e))
            raise LLMError(f"LLM extraction failed: {str(e)}") from e

    def _generate_cache_key(self, code: str, router_prefix: Optional[str]) -> str:
        """Generate cache key for LLM result"""
        import hashlib
        key_parts = [code[:1000], router_prefix or ""]
        return "llm:" + hashlib.sha256("|".join(key_parts).encode()).hexdigest()[:16]

    def is_available(self) -> bool:
        """Check if LLM extractor is available"""
        return self.client is not None