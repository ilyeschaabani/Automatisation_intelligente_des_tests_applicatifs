# GitHub API Discovery Pipeline

Extract OpenAPI specifications from GitHub repositories using a hybrid pipeline:

- Spec import (OpenAPI/Swagger/Postman/Insomnia)
- Config parsing (routes files)
- Static code analysis (Tree-sitter AST + framework regex)
- Universal heuristics (very low confidence)
- Optional AI augmentation (OpenRouter/OpenAI)

This repository is designed to be **multi-language**, **extensible**, and **Windows-friendly**.

## What it does

Given a repository URL, the pipeline:

1. Clones the repo (temporary by default)
2. Detects languages + backend framework(s)
3. Extracts endpoints using a layered strategy
4. Normalizes routes, resolves prefixes, deduplicates
5. Generates `output/<repo_id>_openapi.json` + endpoints + stats

## Layered discovery (4 layers)

The extraction is intentionally ordered from most reliable to least reliable:

1. **Layer 1 — Spec-first (short-circuit)**
	- If the repo already contains OpenAPI/Swagger, Postman, or Insomnia exports, import and output immediately.
2. **Layer 2 — Config routes**
	- Parse framework route config files (e.g., Symfony `routes.yaml`, Rails `routes.rb`, Spring Cloud Gateway config).
3. **Layer 3 — Code routes**
	- Parse code using Tree-sitter (AST) + regex fallback per framework.
	- Optional LLM augmentation can add endpoints for complex patterns.
4. **Layer 4 — Universal fallback**
	- Language-agnostic heuristics scanning text for route-like patterns (very low confidence).

## Supported languages & frameworks (initial registry)

Detection is centralized in `src/registry.py` and used by `src/tech_detector.py`.
Extraction support is a mix of AST and regex patterns (coverage varies by framework).

### Java
- Spring Boot
- Micronaut
- Quarkus (JAX-RS style)

### JavaScript / TypeScript
- Express.js
- NestJS
- Fastify

### Python
- FastAPI
- Flask
- Django (+ Django REST Framework detection)

### PHP
- Symfony
- Laravel

### Go
- Gin
- Echo

### C#
- ASP.NET Core Web API

## Installation

Create a virtual environment (recommended), then:

```bash
pip install -r requirements.txt
```

Optional (LLM support): set `OPENROUTER_API_KEY` or `OPENAI_API_KEY` in your environment.

## Usage

Run via module:

```bash
python -m src.cli https://github.com/owner/repo
```

Or if installed as a package script (see `pyproject.toml`):

```bash
github-api-discovery https://github.com/owner/repo
```

Common options:

```bash
python -m src.cli https://github.com/owner/repo \
  --branch main \
  --log-level INFO \
  --output-dir output \
  --repos-dir C:\\r \
  --max-workers 4
```

Notes:
- Repos are **cleaned up by default** after extraction.
- Use `--keep-repo` to keep a clone for debugging.

## Output artifacts

Artifacts are written under `output/` (configurable) using a stable `repo_id` derived from the URL:

- `output/<repo_id>_openapi.json` — OpenAPI 3.0.3 JSON
- `output/<repo_id>_endpoints.json` — raw endpoints (pre-normalization/dedup)
- `output/<repo_id>_stats.json` — pipeline stats and counters

The OpenAPI file also embeds a minimal runnable project config under `x-discovery` to support deterministic auto-run + auto-test.

## Configuration

The pipeline is configured by CLI flags and environment variables (see `src/config.py`).

Key environment variables:

- `REPOS_DIR` / `OUTPUT_DIR`
- `LOG_LEVEL`
- `MAX_WORKERS`, `MAX_FILE_SIZE`, `CACHE_TTL`
- `OPENROUTER_API_KEY`, `OPENROUTER_MODEL`
- `OPENAI_API_KEY`, `LLM_MODEL`
- `LLM_CONFIDENCE_THRESHOLD`

## Windows notes (path length / locks)

If cloning fails with `Filename too long`, use a short repos directory:

```bash
python -m src.cli https://github.com/owner/repo --repos-dir C:\\r
```

The pipeline also uses temporary clone directories by default to reduce collisions/locks.

## Extending the system

To add a new language/framework in a scalable way:

1. Add detection signals in `src/registry.py`
	- Language indicator files
	- Framework dependency keywords
2. Add extraction logic
	- Layer 2 config parser: extend `src/layered_extractor.py`
	- Layer 3 code patterns: extend `src/regex_extractor.py` (or add AST visitors later)
3. Add tests under `tests/` (keep them small and framework-specific)

## Development

Run unit tests:

```bash
python -m pytest -q
```

## Docs

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- [docs/USAGE.md](docs/USAGE.md)
- [docs/DISCOVERY_CONTRACT.md](docs/DISCOVERY_CONTRACT.md)