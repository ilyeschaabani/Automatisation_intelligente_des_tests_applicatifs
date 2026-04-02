"""Contract interview helper.

This module is intentionally deterministic (no LLM calls).
It inspects a generated OpenAPI document (with x-discovery) and produces:
- missing fields
- low-confidence findings
- a ready-to-use prompt that asks the user only for what is needed
  to make the project runnable for auto-run + auto-test.
"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional


_PLACEHOLDER_URL_RE = re.compile(r"example\\.com|api\\.example\\.com", re.IGNORECASE)


@dataclass(frozen=True)
class Finding:
    kind: str  # "missing" | "low_confidence"
    json_path: str
    reason: str
    evidence: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        out: Dict[str, Any] = {
            "kind": self.kind,
            "json_path": self.json_path,
            "reason": self.reason,
        }
        if self.evidence:
            out["evidence"] = self.evidence
        return out


def _get(obj: Any, path: List[str], default: Any = None) -> Any:
    cur = obj
    for p in path:
        if not isinstance(cur, dict) or p not in cur:
            return default
        cur = cur[p]
    return cur


def _as_list(v: Any) -> List[Any]:
    return v if isinstance(v, list) else []


def _as_dict(v: Any) -> Dict[str, Any]:
    return v if isinstance(v, dict) else {}


def build_findings(openapi: Dict[str, Any]) -> List[Finding]:
    """Return missing + low-confidence findings.

    Notes:
    - We never invent values.
    - We treat x-discovery.discovery.missing as authoritative.
    - We add a few derived findings that are common blockers for runnable tests.
    """

    findings: List[Finding] = []

    xdisc = _as_dict(openapi.get("x-discovery"))
    discovery = _as_dict(xdisc.get("discovery"))

    # 1) Missing fields (authoritative list)
    missing_paths = sorted({str(x) for x in _as_list(discovery.get("missing")) if x})
    for mp in missing_paths:
        findings.append(Finding(kind="missing", json_path=f"$.x-discovery.{mp}", reason="Missing required value"))

    # 2) Derived run checks
    run = _as_dict(xdisc.get("run"))
    services = _as_dict(xdisc.get("services"))

    servers = _as_list(openapi.get("servers"))
    server_url = None
    if servers and isinstance(servers[0], dict):
        server_url = servers[0].get("url")

    base_url = run.get("base_url")
    api_service = run.get("api_service")
    compose_path = run.get("compose_path")
    http_services = _as_list(run.get("http_services"))

    paths_obj = openapi.get("paths")
    has_paths = isinstance(paths_obj, dict) and len(paths_obj) > 0

    if has_paths and not base_url:
        # When we have endpoints but no runnable base_url, testing is blocked.
        findings.append(
            Finding(
                kind="missing",
                json_path="$.x-discovery.run.base_url",
                reason="OpenAPI contains endpoints but no runnable base_url was discovered",
                evidence=f"compose_path={compose_path!r}, api_service={api_service!r}, servers[0].url={server_url!r}",
            )
        )

    # Placeholder server URL is a safety blocker.
    if isinstance(server_url, str) and _PLACEHOLDER_URL_RE.search(server_url):
        findings.append(
            Finding(
                kind="low_confidence",
                json_path="$.servers[0].url",
                reason="Server URL looks like a placeholder; auto-tests should not target remote example hosts",
                evidence=server_url,
            )
        )

    # If api_service is infra, it is almost certainly wrong.
    if isinstance(api_service, str) and api_service in services:
        is_infra = bool(_get(services, [api_service, "is_infra"], False))
        if is_infra:
            findings.append(
                Finding(
                    kind="low_confidence",
                    json_path="$.x-discovery.run.api_service",
                    reason="Selected api_service is marked as infra; likely not the real API entrypoint",
                    evidence=api_service,
                )
            )

    # If compose exists but contains only infra services, it cannot be used to run the API.
    # This commonly happens when the repo has a separate compose for DB/infra.
    if isinstance(compose_path, str) and compose_path:
        if services and all(bool(_get(services, [name, "is_infra"], False)) for name in services.keys()):
            if has_paths:
                findings.append(
                    Finding(
                        kind="low_confidence",
                        json_path="$.x-discovery.run.compose_path",
                        reason=(
                            "compose_path appears to describe infra-only services; it likely does not start the API. "
                            "Provide the compose file that starts the HTTP API services (or confirm you run them outside compose)."
                        ),
                        evidence=f"compose_path={compose_path!r}, services={list(services.keys())}",
                    )
                )

    # If multiple HTTP services exist and we don't have a gateway, runner needs routing rules.
    # We conservatively trigger when >=2 non-infra http_services are present.
    if len(http_services) >= 2:
        routing_rules = _as_list(_as_dict(xdisc.get("routing")).get("rules"))
        if not routing_rules:
            findings.append(
                Finding(
                    kind="missing",
                    json_path="$.x-discovery.routing.rules",
                    reason=(
                        "Multiple HTTP-capable services detected; without a gateway you must map endpoints to services "
                        "so tests hit the correct base_url"
                    ),
                    evidence=f"http_services={[{'service': s.get('service'), 'base_url': s.get('base_url')} for s in http_services if isinstance(s, dict)]}",
                )
            )

    return findings


def render_questionnaire(openapi: Dict[str, Any], findings: List[Finding]) -> Dict[str, Any]:
    """Deterministic questions the user can answer without an LLM.

    This is meant for UX/CLI flows that want to prompt the user directly.
    It returns a structured questionnaire + a patch template (values to fill).
    """

    xdisc = _as_dict(openapi.get("x-discovery"))
    run = _as_dict(xdisc.get("run"))
    auth = _as_dict(xdisc.get("auth"))

    compose_path = run.get("compose_path")
    http_services = [s for s in _as_list(run.get("http_services")) if isinstance(s, dict)]

    missing_paths = {f.json_path for f in findings if f.kind == "missing"}
    low_conf_paths = {f.json_path for f in findings if f.kind == "low_confidence"}

    questions: List[Dict[str, Any]] = []
    asked_paths: set[str] = set()

    def _q(
        *,
        json_path: str,
        reason: str,
        expected_format: str,
        example: str,
        how_to_find: str,
        options: Optional[List[str]] = None,
    ) -> None:
        item: Dict[str, Any] = {
            "json_path": json_path,
            "reason": reason,
            "expected_format": expected_format,
            "example": example,
            "how_to_find": how_to_find,
        }
        if options:
            item["options"] = options
        questions.append(item)
        asked_paths.add(json_path)

    # Ask about compose_path if it looks wrong or is missing.
    if "$.x-discovery.run.compose_path" in low_conf_paths or "$.x-discovery.run.compose_path" in missing_paths:
        _q(
            json_path="$.x-discovery.run.compose_path",
            reason="Current compose file looks infra-only; we need the compose that actually starts the API.",
            expected_format="String path relative to repo root, or null if you don't use compose for the API",
            example="Backend/docker-compose.yml",
            how_to_find="Search the repo for docker-compose.yml/compose.yml that includes the API services (Spring/Node/etc.).",
        )

    # Some generators report missing as "run" (object) when the whole run block is absent.
    # Ask for concrete sub-fields instead of an object-valued path.
    if "$.x-discovery.run" in missing_paths:
        if "$.x-discovery.run.compose_path" not in asked_paths:
            _q(
                json_path="$.x-discovery.run.compose_path",
                reason="Auto-run needs the compose file that starts the API (or confirm you don't use compose).",
                expected_format="String path relative to repo root, or null",
                example="Backend/docker-compose.yml",
                how_to_find="Search the repo for docker compose files; pick the one that starts the HTTP API service.",
            )
        if "$.x-discovery.run.api_service" not in asked_paths:
            _q(
                json_path="$.x-discovery.run.api_service",
                reason="Auto-run must know which compose service is the HTTP API entrypoint.",
                expected_format="One compose service name (string)",
                example="api",
                how_to_find="In the compose file, choose the service exposing HTTP ports for the API.",
            )
        if "$.x-discovery.run.base_url" not in asked_paths:
            _q(
                json_path="$.x-discovery.run.base_url",
                reason="Auto-tests need a concrete base URL to call.",
                expected_format="URL string",
                example="http://localhost:8080",
                how_to_find="Run the stack and identify the published API port, or read the compose ports mapping.",
            )
        if "$.x-discovery.run.healthcheck_path" not in asked_paths:
            _q(
                json_path="$.x-discovery.run.healthcheck_path",
                reason="Runner needs a stable health endpoint to know when the API is ready.",
                expected_format="Path string starting with /",
                example="/actuator/health",
                how_to_find="Check your backend health endpoint (/health, /actuator/health, /status).",
            )

    # Ask about healthcheck_path (required for runner readiness checks).
    if "$.x-discovery.run.healthcheck_path" in missing_paths:
        _q(
            json_path="$.x-discovery.run.healthcheck_path",
            reason="Runner needs a stable health endpoint to know when the API is ready.",
            expected_format="Path string starting with /",
            example="/actuator/health",
            how_to_find="Check your backend health endpoint (/health, /actuator/health, /status) or API gateway readiness route.",
        )

    # Ask for auth type selection when auth is unknown.
    # Note: recompute_discovery_missing uses missing path '$.x-discovery.auth' when type is missing/unknown.
    if "$.x-discovery.auth" in missing_paths:
        _q(
            json_path="$.x-discovery.auth.type",
            reason="Auto-tests must know how to authenticate (or that the API is public).",
            expected_format="One of: none | login_flow | static_token",
            example="login_flow",
            how_to_find="Check your API docs: does it require login to obtain a token, a pre-issued token, or no auth?",
            options=["none", "login_flow", "static_token"],
        )

    # api_service selection
    if "$.x-discovery.run.api_service" in missing_paths:
        if http_services:
            options = [
                f"{s.get('service')} ({s.get('base_url')})" for s in http_services if s.get("service")
            ]
            _q(
                json_path="$.x-discovery.run.api_service",
                reason="We must choose which compose service is the HTTP API entrypoint.",
                expected_format="One compose service name (string)",
                example=http_services[0].get("service") or "api",
                how_to_find="Pick the service that exposes the API you want to test (gateway/api/backend).",
                options=options,
            )
        else:
            _q(
                json_path="$.x-discovery.run.api_service",
                reason="No HTTP services were detected from compose; we still need to know which service is the API.",
                expected_format="One compose service name (string) or null if API isn't in compose",
                example="gateway",
                how_to_find="Check your compose file services and find the one exposing HTTP (ports 80/8080/3000/etc.).",
            )

    # base_url selection
    if "$.x-discovery.run.base_url" in missing_paths:
        _q(
            json_path="$.x-discovery.run.base_url",
            reason="Auto-tests need a concrete base URL to call.",
            expected_format="URL string",
            example="http://localhost:8080",
            how_to_find=(
                "Run `docker compose -f <compose> up` then `docker compose ps` to see published ports, "
                "or read the compose `ports:` section."
            ),
        )

    # Login credentials
    if auth.get("type") == "login_flow":
        # Some generators report missing as "auth.login" (object) when the whole login block is absent.
        # In that case, ask for the concrete leaf fields the user can actually provide.
        if "$.x-discovery.auth.login" in missing_paths:
            if "$.x-discovery.auth.login.endpoint" not in asked_paths:
                _q(
                    json_path="$.x-discovery.auth.login.endpoint",
                    reason="Login-flow auth needs the token/login endpoint path.",
                    expected_format="Path string starting with /",
                    example="/api/auth/login",
                    how_to_find="Search your backend for the login route or check README / Swagger security docs.",
                )
            if "$.x-discovery.auth.login.username" not in asked_paths:
                _q(
                    json_path="$.x-discovery.auth.login.username",
                    reason="Login-flow auth needs a username/email for token acquisition.",
                    expected_format="String",
                    example="user@example.com",
                    how_to_find="Use a test account in the project seed data, or create one via the signup endpoint.",
                )
            if "$.x-discovery.auth.login.password" not in asked_paths:
                _q(
                    json_path="$.x-discovery.auth.login.password",
                    reason="Login-flow auth needs a password for token acquisition.",
                    expected_format="String",
                    example="P@ssw0rd123",
                    how_to_find="Use the password of the test account; if unknown, check README/seed scripts or create one.",
                )

        if "$.x-discovery.auth.login.endpoint" in missing_paths:
            _q(
                json_path="$.x-discovery.auth.login.endpoint",
                reason="Login-flow auth needs the token/login endpoint path.",
                expected_format="Path string starting with /",
                example="/api/auth/login",
                how_to_find="Search your backend for the login route or check README / Swagger security docs.",
            )
        if "$.x-discovery.auth.login.username" in missing_paths:
            _q(
                json_path="$.x-discovery.auth.login.username",
                reason="Login-flow auth needs a username/email for token acquisition.",
                expected_format="String",
                example="user@example.com",
                how_to_find="Use a test account in the project seed data, or create one via the signup endpoint.",
            )
        if "$.x-discovery.auth.login.password" in missing_paths:
            _q(
                json_path="$.x-discovery.auth.login.password",
                reason="Login-flow auth needs a password for token acquisition.",
                expected_format="String",
                example="P@ssw0rd123",
                how_to_find="Use the password of the test account; if unknown, check README/seed scripts or create one.",
            )

    if auth.get("type") == "static_token":
        if "$.x-discovery.auth.static_token" in missing_paths:
            _q(
                json_path="$.x-discovery.auth.static_token",
                reason="Static-token auth needs a token value to authorize requests.",
                expected_format="String (token value only)",
                example="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                how_to_find="Generate a token using your auth system or use a pre-issued test token from your secrets manager.",
            )

    # Fallback: ensure we always emit a question for missing required fields.
    # This prevents UX flows from stalling on needs_user_input with an empty question list.
    for mp in sorted(missing_paths):
        if mp in asked_paths:
            continue
        if mp == "$.x-discovery.auth":
            # We ask for auth.type above.
            continue
        # Never ask users to fill object-valued paths.
        if mp in {"$.x-discovery.run", "$.x-discovery.auth.login", "$.x-discovery.auth.login"}:
            continue
        if mp.endswith(".auth.login") or mp.endswith(".x-discovery.auth.login"):
            continue
        if not (mp.startswith("$.x-discovery.run.") or mp.startswith("$.x-discovery.auth.")):
            continue
        _q(
            json_path=mp,
            reason="Missing required value for runnable auto-test configuration.",
            expected_format="String",
            example="(provide a value)",
            how_to_find="Check project documentation and runtime configuration; fill the correct value for this field.",
        )

    patch_template: Dict[str, Any] = {
        "x-discovery": {
            "run": {
                # User can fill these if asked
                "compose_path": compose_path,
                "api_service": run.get("api_service"),
                "base_url": run.get("base_url"),
                "healthcheck_path": run.get("healthcheck_path"),
            },
            "auth": {
                "type": auth.get("type"),
                "login": {
                    "endpoint": _get(auth, ["login", "endpoint"], None),
                    "username": _get(auth, ["login", "username"], None),
                    "password": _get(auth, ["login", "password"], None),
                }
                if auth.get("type") == "login_flow"
                else {**auth, "static_token": auth.get("static_token")},
            },
        }
    }

    return {
        "findings": [f.to_dict() for f in findings],
        "questions": questions,
        "patch_template": patch_template,
    }


def render_llm_prompt(openapi: Dict[str, Any], findings: List[Finding]) -> str:
    """Return a prompt for an LLM to interview the user and output a JSON Merge Patch."""

    xdisc = _as_dict(openapi.get("x-discovery"))
    run = _as_dict(xdisc.get("run"))
    auth = _as_dict(xdisc.get("auth"))

    # Keep context small: include only the bits relevant to runnable automation.
    context_obj = {
        "servers": openapi.get("servers"),
        "x-discovery": {
            "repo": xdisc.get("repo"),
            "run": {
                "strategy": run.get("strategy"),
                "compose_path": run.get("compose_path"),
                "api_service": run.get("api_service"),
                "published_port": run.get("published_port"),
                "base_url": run.get("base_url"),
                "healthcheck_path": run.get("healthcheck_path"),
                "http_services": run.get("http_services"),
            },
            "auth": auth,
            "secrets_env": xdisc.get("secrets_env"),
            "routing": xdisc.get("routing"),
            "discovery": xdisc.get("discovery"),
        },
    }

    findings_lines = "\n".join(
        f"- [{f.kind}] {f.json_path}: {f.reason}" + (f" (evidence: {f.evidence})" if f.evidence else "")
        for f in findings
    )

    context_json = json.dumps(context_obj, indent=2, ensure_ascii=False)

    return f"""You are a Discovery Contract Validator for automated API run + test.

You will interview the user to fill ONLY missing or low-confidence fields.

Rules:
- Do NOT invent values. If unknown, ask.
- Ask the MINIMUM number of questions required to make auto-run + auto-test deterministic.
- Prefer closed questions with options.
- Each question MUST include: json_path, reason, expected_format, example, how_to_find.
- Output MUST end with a JSON Merge Patch that sets only confirmed answers.

Findings (missing/low confidence):
{findings_lines or "(none)"}

Context (excerpt of OpenAPI + x-discovery):
{context_json}

Now produce:
1) Findings recap (brief)
2) Questions (numbered; minimal set)
3) Proposed JSON Merge Patch (RFC 7396)
4) Resulting readiness: runnable | not_runnable
"""


def render_llm_json_request(openapi: Dict[str, Any], findings: List[Finding]) -> Dict[str, str]:
    """Return system+user prompts that force a machine-readable JSON output.

    Output schema expected from the LLM:
    {
      "questions": [
        {
          "json_path": "$.x-discovery.run.base_url",
          "reason": "...",
          "expected_format": "...",
          "example": "...",
          "how_to_find": "...",
          "options": ["..."]
        }
      ],
      "json_merge_patch": { ... },
      "readiness": "runnable"|"not_runnable"
    }
    """

    xdisc = _as_dict(openapi.get("x-discovery"))
    run = _as_dict(xdisc.get("run"))
    auth = _as_dict(xdisc.get("auth"))

    context_obj = {
        "servers": openapi.get("servers"),
        "x-discovery": {
            "repo": xdisc.get("repo"),
            "run": {
                "strategy": run.get("strategy"),
                "compose_path": run.get("compose_path"),
                "api_service": run.get("api_service"),
                "published_port": run.get("published_port"),
                "base_url": run.get("base_url"),
                "healthcheck_path": run.get("healthcheck_path"),
                "http_services": run.get("http_services"),
            },
            "auth": auth,
            "secrets_env": xdisc.get("secrets_env"),
            "routing": xdisc.get("routing"),
            "discovery": xdisc.get("discovery"),
        },
    }

    system_prompt = """You are a Discovery Contract Validator for automated API run + test.

CRITICAL RULES:
- Respond with ONLY valid JSON (no markdown, no extra text).
- Do NOT invent values. If unknown, ask a question.
- Ask the MINIMUM number of questions required to make the project runnable for auto-run + auto-test.
- Prefer closed questions with options.

Return JSON with keys: questions (array), json_merge_patch (object), readiness (string).
"""

    findings_lines = "\n".join(
        f"- [{f.kind}] {f.json_path}: {f.reason}" + (f" (evidence: {f.evidence})" if f.evidence else "")
        for f in findings
    )

    user_prompt = (
        "You must help the user complete/correct missing or low-confidence discovery config.\n\n"
        "Findings:\n"
        + (findings_lines or "(none)")
        + "\n\nContext JSON:\n"
        + json.dumps(context_obj, indent=2, ensure_ascii=False)
        + "\n\nNow output ONLY JSON using this schema:\n"
        + json.dumps(
            {
                "questions": [
                    {
                        "json_path": "$.x-discovery.run.base_url",
                        "reason": "Why we need it",
                        "expected_format": "What the user should provide",
                        "example": "Example value",
                        "how_to_find": "How user can find it",
                        "options": ["optional choices"],
                    }
                ],
                "json_merge_patch": {"x-discovery": {}},
                "readiness": "not_runnable",
            },
            indent=2,
            ensure_ascii=False,
        )
        + "\n\nNotes:\n"
        + "- json_merge_patch MUST set ONLY fields that are confirmed/known from context. Use null when unknown.\n"
        + "- If multiple http_services exist, include a question to build x-discovery.routing.rules.\n"
    )

    return {"system": system_prompt, "user": user_prompt}


def _load_json(path: Path) -> Dict[str, Any]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("OpenAPI JSON must be an object")
    return data


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Generate an LLM interview prompt for x-discovery completion")
    parser.add_argument("openapi", type=str, help="Path to *_openapi.json")
    parser.add_argument("--format", choices=["prompt", "json", "questionnaire"], default="prompt")
    parser.add_argument("--out", type=str, default=None, help="Write output to file instead of stdout")

    args = parser.parse_args(argv)

    openapi_path = Path(args.openapi)
    openapi = _load_json(openapi_path)

    findings = build_findings(openapi)

    if args.format == "questionnaire":
        out_text = json.dumps(render_questionnaire(openapi, findings), indent=2, ensure_ascii=False)
    elif args.format == "json":
        payload = {
            "findings": [f.to_dict() for f in findings],
            "prompt": render_llm_prompt(openapi, findings),
        }
        out_text = json.dumps(payload, indent=2, ensure_ascii=False)
    else:
        out_text = render_llm_prompt(openapi, findings)

    if args.out:
        Path(args.out).write_text(out_text, encoding="utf-8")
    else:
        print(out_text)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
