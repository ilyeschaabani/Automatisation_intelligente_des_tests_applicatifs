# GitHub API Discovery Pipeline - Usage Guide

## Quick Start

1. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

2. **Optional: enable LLM augmentation (cloud or local):**

    The pipeline can optionally call an LLM to improve extraction for tricky/dynamic patterns.

    **Option A — Local (recommended for "Option C", e.g. Ollama/vLLM/TGI):**
    - Start a local OpenAI-compatible server.
       - Ollama: typically `http://localhost:11434/v1`
       - vLLM: typically `http://localhost:8000/v1`
    - Configure environment variables:
       ```bash
       cp .env.example .env
       # Edit .env:
       #   LLM_PROVIDER=local
       #   LLM_BASE_URL=http://localhost:11434/v1
       #   LLM_MODEL=qwen2.5-coder:7b
       #   LLM_API_KEY=local
       ```

    **Optional: final-stage LLM verification (recommended for test generation):**

    You can enable a final pipeline stage that asks the LLM to **validate request templates**
    (path/query/header/body schemas + examples) and apply **safe corrections** (fill missing keys,
    fill missing examples, enforce path params required).

    - Enable verification:
       ```bash
       # .env
       LLM_VERIFY=true
       ```
    - Enable strict mode (pipeline fails if outputs are still not test-ready):
       ```bash
       # .env
       LLM_VERIFY_STRICT=true
       ```
    - Optional limits (to control token usage):
       ```bash
       # .env
       LLM_VERIFY_MAX_ENDPOINTS=40
       LLM_VERIFY_CHUNK_SIZE=15
       ```

    Notes:
    - This step is best-effort and is designed to avoid hallucinating DTO fields.
    - Strict mode improves reliability by refusing to produce outputs that fail structural checks.

    **Option B — OpenRouter (cloud):**
    - Set `OPENROUTER_API_KEY` (and optionally `OPENROUTER_MODEL`).

    **Option C — OpenAI (cloud):**
    - Set `OPENAI_API_KEY` (and optionally `LLM_MODEL`).

3. **Run the pipeline:**
   ```bash
   python -m src.cli https://github.com/owner/repository
   ```

## Command-Line Options

```
usage: github-api-discovery [-h] [-b BRANCH] [--keep-repo] [--log-level {DEBUG,INFO,WARNING,ERROR}]
                            [--output-dir OUTPUT_DIR] [--repos-dir REPOS_DIR] [--max-workers MAX_WORKERS]
                            repo_url

positional arguments:
  repo_url               GitHub repository URL

optional arguments:
  -h, --help            show this help message and exit
  -b BRANCH, --branch BRANCH
                        Branch to clone (default: default branch)
  --keep-repo           Keep cloned repository for debugging
  --log-level {DEBUG,INFO,WARNING,ERROR}
                        Logging level (default: INFO)
  --output-dir OUTPUT_DIR
                        Output directory for results (default: output/)
   --repos-dir REPOS_DIR  Directory for cloned repositories (default: repos/)
  --max-workers MAX_WORKERS
                        Number of parallel workers (default: 4)
  --version             show program's version number and exit
```

## Output Files

The pipeline generates three files in the output directory:

1. **`<repo>_openapi.json`** - OpenAPI 3.0 specification
2. **`<repo>_endpoints.json`** - Raw extracted endpoints (for debugging)
3. **`<repo>_stats.json`** - Pipeline execution statistics

## Supported Frameworks

### Node.js
- Express
- Fastify
- Koa
- NestJS
- Hapi

### Python
- Flask
- FastAPI
- Django
- Bottle
- Tornado

### Java
- Spring Boot
- JAX-RS

### C#
- ASP.NET Core
- Web API

### Go
- Gin
- Echo
- Gorilla Mux
- Fiber

## Architecture

The pipeline consists of the following components:

1. **Repository Manager** - Clones and manages repository lifecycle
2. **Tech Stack Detector** - Identifies languages and frameworks
3. **File Scanner** - Pre-filters files using regex patterns
4. **AST Parser** - Tree-sitter based parsing for accurate extraction
5. **Chunking Engine** - Handles large files intelligently
6. **LLM Extractor** - Augments extraction for complex cases
7. **Endpoint Processor** - Normalizes, deduplicates, resolves prefixes
8. **Route Resolver** - Resolves nested router prefixes
9. **OpenAPI Generator** - Generates valid OpenAPI 3.0 spec

## Performance

- Parallel file processing using ProcessPoolExecutor
- In-memory caching of parsed files
- Shallow git clones for speed
- Configurable worker count

## Error Handling

- Graceful degradation per file (pipeline continues)
- Comprehensive logging
- Retry logic for git operations
- Detailed error reporting in stats

## Limitations

- LLM extraction is optional and requires either a cloud key (OpenRouter/OpenAI) or a local OpenAI-compatible endpoint
- LLM verification is optional; it can improve structural completeness but cannot guarantee semantic correctness of the upstream code
- Some dynamic route patterns may be missed
- Very large repositories may require more memory
- GraphQL support is limited to detection

## Troubleshooting

### No endpoints found
- Check that the repository contains API code
- Verify supported framework is used
- Increase log level to DEBUG for more details

### Parser errors
- Ensure tree-sitter-language-pack is installed
- Check file encoding (UTF-8 required)

### Slow performance
- Reduce `--max-workers` if memory constrained
- Use cached repositories (automatic)
- Consider repository size

## Development

Run tests:
```bash
pytest tests/ -v
```

Run with coverage:
```bash
pytest tests/ --cov=src --cov-report=html
```

Lint:
```bash
flake8 src/
black src/ --check
mypy src/