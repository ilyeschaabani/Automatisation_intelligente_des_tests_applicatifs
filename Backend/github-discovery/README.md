# GitHub API Discovery Pipeline

A production-ready, scalable pipeline that extracts complete OpenAPI 3.0 specifications from GitHub repositories.

## Features

- Automatic repository cloning with retry logic
- Multi-language tech stack detection
- AST-based endpoint extraction (Tree-sitter)
- Intelligent chunking for large files
- LLM augmentation for complex cases
- Route prefix resolution and deduplication
- Parallel processing with caching
- Comprehensive error handling and logging

## Installation

```bash
pip install -r requirements.txt
```

## Usage

```bash
python -m src.main https://github.com/owner/repo
```

## Output

- `openapi.json` - OpenAPI 3.0 specification
- `endpoints.json` - Raw extracted endpoints
- `pipeline.log` - Detailed execution logs