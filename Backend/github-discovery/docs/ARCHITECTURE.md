# System Architecture

## Overview

The GitHub API Discovery Pipeline is a modular, production-grade system for extracting OpenAPI 3.0 specifications from GitHub repositories. It uses a combination of static analysis (AST parsing) and optional LLM augmentation to discover API endpoints with high accuracy.

## Architecture Diagram

```
┌─────────────────┐
│   CLI / API     │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────────┐
│          Pipeline Orchestrator              │
│  (Coordinates all components)              │
└─────────┬────────────┬─────────────────────┘
          │            │
    ┌─────▼─────┐  ┌──▼────────────┐
    │ Repository│  │  Tech Stack   │
    │  Manager  │  │  Detector     │
    └─────┬─────┘  └───────────────┘
          │
          ▼
    ┌─────────────────┐
    │   File Scanner  │
    └────────┬────────┘
             │
             ▼
    ┌─────────────────────────────────────┐
    │        AST Parser Engine            │
    │  (Tree-sitter for multi-language)  │
    └────────┬───────────────────────────┘
             │
             ├──────────────┐
             │              │
      ┌──────▼──────┐  ┌────▼──────┐
      │  Chunking   │  │   LLM     │
      │   Engine    │  │ Extractor │
      └─────────────┘  └───────────┘
             │
             ▼
    ┌─────────────────────────────────────┐
    │     Endpoint Processor              │
    │  (Normalize, Deduplicate, Resolve) │
      └────────┬──────────────────────────┘
               │
               ▼
    ┌─────────────────────────────────────┐
    │      Route Resolver                 │
    │  (Prefix resolution, imports)      │
      └────────┬──────────────────────────┘
               │
               ▼
    ┌─────────────────────────────────────┐
    │      OpenAPI Generator              │
    │  (OpenAPI 3.0 spec creation)       │
      └────────┬──────────────────────────┘
               │
               ▼
            ┌──────┐
            │ JSON │
            │ Files│
            └──────┘
```

## Component Details

### 1. Repository Manager

**File:** `src/repo_manager.py`

Responsibilities:
- Clone GitHub repositories with retry logic
- Shallow clones for performance
- Cache management
- Cleanup

Key Features:
- SHA256-based repository ID generation
- Configurable retry count (default: 3)
- Timeout handling (default: 300s)
- Automatic cleanup on failure

### 2. Tech Stack Detector

**File:** `src/tech_detector.py`

Responsibilities:
- Detect programming languages
- Identify frameworks (Express, Flask, Spring, etc.)
- Detect GraphQL usage
- Support multi-stack repositories

Detection Methods:
- File presence (package.json, requirements.txt, pom.xml, etc.)
- Package file content analysis
- Pattern matching for GraphQL

### 3. File Scanner

**File:** `src/file_scanner.py`

Responsibilities:
- Recursively scan repository
- Filter out irrelevant directories (node_modules, venv, etc.)
- Pre-filter files using regex patterns
- Identify candidate files for AST parsing

Regex Patterns:
- Express routes: `app.get|post|...`
- Flask decorators: `@app.route`
- Spring annotations: `@GetMapping`
- ASP.NET attributes: `[HttpGet]`

### 4. AST Parser Engine

**File:** `src/ast_parser/`

Responsibilities:
- Parse source files using Tree-sitter
- Language-specific node visitors
- Extract HTTP methods, paths, function names
- Handle router prefixes

Supported Languages:
- JavaScript/TypeScript (Node.js)
- Python (Flask, FastAPI)
- Java (Spring Boot)
- C# (ASP.NET Core)
- Go (Gin, Echo, etc.)

Visitor Classes:
- `JavaScriptVisitor` - Express, Koa, etc.
- `PythonVisitor` - Flask, FastAPI
- `JavaVisitor` - Spring
- `CSharpVisitor` - ASP.NET
- `GoVisitor` - Gin, Echo, Mux

### 5. Chunking Engine

**File:** `src/chunker.py`

Responsibilities:
- Split large files into manageable chunks
- Preserve context (imports, class definitions)
- Overlapping sliding window for safety
- Hash-based caching

Strategies:
1. Logical block splitting (by function/class)
2. Sliding window with overlap (fallback)

### 6. LLM Extractor

**File:** `src/llm_extractor.py`

Responsibilities:
- Augment AST extraction for complex cases
- Handle dynamic routes
- Process custom abstractions

Usage:
- Only called when AST fails or confidence is low
- Configurable via `OPENAI_API_KEY`
- Caches results

Prompt Strategy:
- System prompt defines JSON output format
- Includes file path, router prefix, code chunk
- Temperature: 0.1 for consistency

### 7. Endpoint Processor

**File:** `src/endpoint_processor.py`

Responsibilities:
- Normalize paths (remove duplicates slashes, trailing /)
- Resolve router prefixes
- Deduplicate endpoints (method + full_path)
- Detect conflicts

Deduplication Key: `(HTTPMethod, full_path)`

### 8. Route Resolver

**File:** `src/route_resolver.py`

Responsibilities:
- Analyze files for `app.use('/prefix', router)` patterns
- Build import graph to trace router relationships
- Resolve nested router prefixes
- Infer prefixes from file paths

Features:
- Language-specific prefix extraction
- Import resolution (JS, Python, Java, C#, Go)
- Prefix caching

### 9. OpenAPI Generator

**File:** `src/models/openapi.py`

Responsibilities:
- Generate valid OpenAPI 3.0.3 specification
- Group endpoints by path
- Add standard responses (200, 400, 401, 403, 404, 500)
- Generate tags from file paths
- Add security schemes (Bearer JWT)

Output Structure:
```json
{
  "openapi": "3.0.3",
  "info": { "title", "version", "description" },
  "servers": [{ "url": "https://api.example.com" }],
  "paths": { "/path": { "get": { ... } } },
  "components": { "securitySchemes": {...} },
  "tags": [...]
}
```

### 10. Pipeline Orchestrator

**File:** `src/pipeline.py`

Responsibilities:
- Coordinate all components
- Error handling and recovery
- Statistics collection
- Output generation

Execution Flow:
1. Clone repository
2. Detect tech stack
3. Scan files
4. Resolve prefixes
5. Parse files (AST + optional LLM)
6. Process endpoints
7. Generate OpenAPI
8. Save outputs
9. Cleanup

### 11. CLI Interface

**File:** `src/cli.py`

Responsibilities:
- Command-line argument parsing
- Configuration from environment
- User-friendly output
- Error reporting

## Data Flow

```
GitHub URL
    │
    ▼
Clone ──────────────┐
    │              │
    ▼              │
Detect Stack       │
    │              │
    ▼              │
Scan Files ◄───────┘
    │
    ▼
Resolve Prefixes (import graph)
    │
    ▼
Parse Files (AST) ──► LLM Fallback (optional)
    │
    ▼
Collect Endpoints
    │
    ▼
Process (Normalize, Dedup)
    │
    ▼
Generate OpenAPI
    │
    ▼
Save JSON + Stats
```

## Error Handling Strategy

- **Per-file errors**: Continue processing other files
- **Clone failures**: Retry up to 3 times, then abort
- **AST failures**: Log error, skip file
- **LLM failures**: Log warning, continue without augmentation
- **Unexpected errors**: Catch, log, continue

All errors are collected in `stats.errors` for review.

## Performance Optimizations

1. **Parallelism**: ProcessPoolExecutor for AST parsing
2. **Caching**: In-memory cache for LLM results and clone status
3. **Shallow clones**: `--depth=1` for faster cloning
4. **Pre-filtering**: Regex scan before AST parsing
5. **Chunking**: Process large files in pieces

## Configuration

Environment variables (`.env`):
- `OPENAI_API_KEY` - OpenAI API key
- `LLM_MODEL` - Model to use (default: gpt-4)
- `MAX_FILE_SIZE` - Chunking threshold (bytes)
- `MAX_WORKERS` - Parallel workers
- `CACHE_TTL` - Cache TTL (seconds)
- `LOG_LEVEL` - Logging level

## Extensibility

To add a new language:
1. Add language to `LANGUAGE_MAPPING` in `TreeSitterParser`
2. Create visitor class inheriting from `NodeVisitor`
3. Implement `visit()` method
4. Register in `get_visitor_for_language()`

To add a new framework detection:
1. Add to `LANGUAGE_INDICATORS` in `TechStackDetector`
2. Add parsing logic in `_detect_from_package_files()`

## Testing

- Unit tests for each component
- Integration tests with sample repos
- Mock external dependencies (git, LLM)
- Coverage reporting

Run: `pytest tests/ -v --cov=src`