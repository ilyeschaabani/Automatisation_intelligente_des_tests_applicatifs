"""Apply user/LLM patches to a generated OpenAPI + x-discovery contract.

This supports a workflow:
1) Generate OpenAPI with x-discovery (may be incomplete)
2) Produce a JSON Merge Patch (RFC 7396) from user answers (possibly via LLM)
3) Apply patch deterministically to create a test-runnable artifact

No network, no LLM calls.
"""

from __future__ import annotations

from copy import deepcopy
from pathlib import Path
from typing import Any, Dict, List, Optional


def apply_json_merge_patch(target: Any, patch: Any) -> Any:
    """Apply RFC 7396 JSON Merge Patch.

    - If patch is an object, merge keys into target.
    - If a patch value is null, delete that key.
    - Otherwise, replace.

    Works on standard JSON types.
    """

    if not isinstance(patch, dict):
        return deepcopy(patch)

    if not isinstance(target, dict):
        target = {}

    result: Dict[str, Any] = deepcopy(target)

    for key, value in patch.items():
        if value is None:
            result.pop(key, None)
            continue

        if isinstance(value, dict):
            result[key] = apply_json_merge_patch(result.get(key), value)
        else:
            result[key] = deepcopy(value)

    return result


def recompute_discovery_missing(openapi: Dict[str, Any]) -> List[str]:
    """Compute x-discovery.discovery.missing from the current document.

    This intentionally mirrors the minimal completeness definition.
    """

    xdisc = openapi.get("x-discovery") if isinstance(openapi, dict) else None
    if not isinstance(xdisc, dict):
        return ["x-discovery"]

    missing: List[str] = []

    run = xdisc.get("run")
    if not isinstance(run, dict):
        missing.append("run")
    else:
        if not run.get("compose_path"):
            missing.append("run.compose_path")
        if not run.get("api_service"):
            missing.append("run.api_service")
        if not run.get("base_url"):
            missing.append("run.base_url")
        if not run.get("healthcheck_path"):
            missing.append("run.healthcheck_path")

    # DB is required to be explicitly known (yes/no). If required, type/url are required.
    db = xdisc.get("db")
    if not isinstance(db, dict):
        missing.append("db.required")
    else:
        required = db.get("required")
        if required is None:
            missing.append("db.required")
        elif required is True:
            if not db.get("type"):
                missing.append("db.type")
            if not db.get("url_env_var"):
                missing.append("db.url_env_var")

    auth = xdisc.get("auth")
    if not isinstance(auth, dict):
        missing.append("auth")
    else:
        auth_type = auth.get("type")
        if not auth_type or auth_type == "unknown":
            missing.append("auth")
        elif auth_type == "login_flow":
            login = auth.get("login")
            if not isinstance(login, dict):
                missing.append("auth.login")
            else:
                if not login.get("endpoint"):
                    missing.append("auth.login.endpoint")
                if not login.get("username"):
                    missing.append("auth.login.username")
                if not login.get("password"):
                    missing.append("auth.login.password")
        elif auth_type == "static_token":
            if not auth.get("static_token"):
                missing.append("auth.static_token")

    # required_env_vars is not strictly blocking (platform can inject), but keep structure sane
    secrets_env = xdisc.get("secrets_env")
    if secrets_env is not None and not isinstance(secrets_env, dict):
        missing.append("secrets_env")

    return sorted(set(missing))


def align_openapi_servers(openapi: Dict[str, Any]) -> None:
    """Align servers[0].url with x-discovery.run.base_url when present."""

    if not isinstance(openapi, dict):
        return

    xdisc = openapi.get("x-discovery")
    if not isinstance(xdisc, dict):
        return

    run = xdisc.get("run")
    if not isinstance(run, dict):
        return

    base_url = run.get("base_url")
    if not base_url:
        return

    servers = openapi.get("servers")
    if isinstance(servers, list) and servers and isinstance(servers[0], dict):
        servers[0]["url"] = base_url
    else:
        openapi["servers"] = [{"url": base_url}]

    info = openapi.get("info")
    if isinstance(info, dict):
        info["x-discovery-base-url"] = base_url


def apply_discovery_patch(openapi: Dict[str, Any], patch: Dict[str, Any]) -> Dict[str, Any]:
    """Apply a JSON merge patch and keep derived fields consistent."""

    updated = apply_json_merge_patch(openapi, patch)
    if not isinstance(updated, dict):
        raise ValueError("Patch application produced non-object OpenAPI")

    align_openapi_servers(updated)

    # Recompute missing list
    xdisc = updated.get("x-discovery")
    if isinstance(xdisc, dict):
        discovery = xdisc.get("discovery")
        if not isinstance(discovery, dict):
            discovery = {}
            xdisc["discovery"] = discovery
        discovery["missing"] = recompute_discovery_missing(updated)
        discovery.setdefault("notes", [])

    return updated


def _load_json(path: str) -> Any:
    import json

    return json.loads(Path(path).read_text(encoding="utf-8"))


def main(argv: Optional[List[str]] = None) -> int:
    import argparse
    import json
    from pathlib import Path

    parser = argparse.ArgumentParser(description="Apply a JSON Merge Patch to an OpenAPI + x-discovery file")
    parser.add_argument("--openapi", required=True, help="Path to *_openapi.json")
    parser.add_argument("--patch", required=True, help="Path to JSON Merge Patch (RFC 7396)")
    parser.add_argument("--out", default=None, help="Output file path (default: overwrite --openapi)")

    args = parser.parse_args(argv)

    openapi_obj = _load_json(args.openapi)
    if not isinstance(openapi_obj, dict):
        raise SystemExit("OpenAPI must be a JSON object")

    patch_obj = _load_json(args.patch)
    if not isinstance(patch_obj, dict):
        raise SystemExit("Patch must be a JSON object (RFC 7396)")

    updated = apply_discovery_patch(openapi_obj, patch_obj)

    out_path = Path(args.out) if args.out else Path(args.openapi)
    out_path.write_text(json.dumps(updated, indent=2, ensure_ascii=False, default=str), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
