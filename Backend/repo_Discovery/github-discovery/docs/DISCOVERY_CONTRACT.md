# x-discovery Contract (Minimal Project Config)

The pipeline emits a **minimal runnable project configuration** inside the generated OpenAPI as a vendor extension:

- `x-discovery`: project metadata required to make **auto-run + auto-test** deterministic.

This document defines the current contract.

## Where it lives

In the generated file `*_openapi.json`:

- Top-level: `openapi.servers[0].url` is set to the discovered `base_url` when available.
- Top-level: `x-discovery` contains the minimal project config.

## x-discovery schema (current)

### 1) Repo

`x-discovery.repo`

- `provider`: `"github" | "gitlab"` (best-effort guess from URL)
- `url`: repository clone URL
- `branch`: default branch (string; best-effort, defaults to `"main"` when unknown)

### 2) Run strategy (Docker Compose)

`x-discovery.run`

- `strategy`: currently always `"docker_compose"`
- `compose_path`: path to compose file relative to repo root (e.g. `"docker-compose.yml"`) or `null`
- `api_service`: docker-compose service name considered the “API entrypoint” (e.g. `"gateway"`) or `null`
- `published_port`: published port (int) selected from `api_service.ports` or `null`
- `base_url`: computed from `published_port` as `http://localhost:{published_port}` or `null`
- `healthcheck_path`: string path used by the runner to wait for readiness (e.g. `"/health"` or `"/actuator/health"`)
- `http_services`: ranked list of non-infra services that expose an HTTP-ish port. Each item contains:
   - `service`: compose service name
   - `published_port`: chosen port (int)
   - `base_url`: `http://localhost:{published_port}`
   - `score`: internal heuristic score (higher = more likely entrypoint)

Runner expectations:

- When `base_url` is not null, the generator sets `servers[0].url` to it.
- When `compose_path` or `api_service` is null, the platform should request user input or apply repo-specific overrides.

### 3) Auth strategy

`x-discovery.auth`

Common fields:

- `header_name`: typically `"Authorization"`
- `header_template`: typically `"Bearer {token}"`

Auth modes:

1) `type: "login_flow"`
   - `login.endpoint`: login endpoint path (e.g. `"/api/auth/signin"`)
   - `login.username`: `null` until provided
   - `login.password`: `null` until provided
   - `login.token_field`: `null` (never guessed; should be set only after observing real responses)
   - `static_token`: `null`

2) `type: "static_token"` (future-compatible)
   - `static_token`: string token
   - `login`: `null`

3) `type: "unknown"`
   - Used when no safe guess can be made.

Runner expectations:

- If `type == "login_flow"`, the platform must inject `username/password` and implement token extraction.
- Token extraction should not rely on a single field name; prefer scanning the JSON response for common keys.

### 4) Secrets / env injection

`x-discovery.secrets_env`

- `required_env_vars`: list of env var names detected from compose placeholders (e.g. `${DB_URL}` → `"DB_URL"`).

Runner expectations:

- The platform should prompt for these env values (or pull from its own secret store) and inject them at runtime.

### 5) Services inventory (optional but useful)

`x-discovery.services` is a dictionary keyed by docker-compose service name.
Each service may include:

- `ports`: list of `{ published: int | null, target: int | null }`
- `depends_on`: raw compose value (list/dict/null)
- `environment`: raw compose value (dict/list/null)
- `image`: string/null
- `build`: raw compose value (string/dict/null)
- `http.base_url`: best-effort computed URL from ports (or null)
- `http.published_port`: chosen port (or null)
- `is_infra`: bool flag used by the generator to exclude DB/admin/broker services from API entrypoint selection

### 6) Discovery status

`x-discovery.discovery`

- `missing`: list of missing required pieces (e.g. `"auth.login.username"`, `"run.compose_path"`)
- `notes`: free-form list of strings for non-fatal issues / best-effort fallbacks

Platform expectation:

- Treat non-empty `missing` as a **blocking** signal for “auto-run + auto-test” unless your platform has overrides.

### 7) Routing (optional; for gateway-less multi-service)

When a repository contains multiple HTTP-capable services (no API gateway), a runner cannot reliably test all endpoints against a single `base_url`.

To make routing deterministic, the platform (or the user via an interview step) can add:

`x-discovery.routing`

- `rules`: list of routing rules. Each rule maps a set of endpoints to a compose service.

Rule format (minimal):

- `type`: currently one of:
   - `"path_prefix"` — match by URL prefix
   - `"tag"` — match by OpenAPI tag name
- `value`: the prefix or tag to match
- `service`: compose service name (must exist in `x-discovery.services` when compose is available)

Optional:

- `default_service`: compose service name used when no rule matches.

Runner expectations:

- If `x-discovery.run.http_services` has multiple candidates and there is no gateway, treat missing/empty `x-discovery.routing.rules` as a **blocking** condition and prompt the user.

## Minimal completeness definition (for deterministic auto-run + auto-test)

A project is “ready” when all of these are present (non-null / non-empty):

- Repo: `provider`, `url`, `branch`
- Run: `strategy == "docker_compose"`, `compose_path`, `api_service`, `base_url`, `healthcheck_path`
- Auth:
  - either `type == "static_token"` with `static_token`
  - or `type == "login_flow"` with `login.endpoint`, `login.username`, `login.password`
- Secrets/env: platform can satisfy `required_env_vars`

## Notes on OpenAPI usability for auth

The generator may add a flexible schema `components.schemas.AuthTokensResponse` and reference it from login/refresh endpoints so runners can parse token responses even when the exact field name differs.
