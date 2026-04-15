from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request


def _post_json(url: str, payload: dict) -> tuple[int, dict]:
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req) as resp:
            body = resp.read().decode("utf-8", errors="ignore")
            return resp.status, json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="ignore")
        try:
            parsed = json.loads(body) if body else {}
        except Exception:
            parsed = {"raw": body}
        return e.code, parsed


def _is_secret_key(key: str) -> bool:
    key_u = (key or "").upper()
    return any(tok in key_u for tok in ["PASSWORD", "SECRET", "TOKEN", "KEY"]) and "HOST_PORT" not in key_u


def main() -> int:
    if len(sys.argv) < 2:
        print("Usage: python interactive_prepare.py <git_url> [branch]")
        return 2

    target = sys.argv[1]
    branch = sys.argv[2] if len(sys.argv) >= 3 else "main"

    base = "http://127.0.0.1:8000"

    # First call: standby mode enabled
    status, payload = _post_json(
        f"{base}/prepare",
        {
            "target": target,
            "branch": branch,
            "fail_on_missing": True,
            "auto_assign_host_ports": True,
        },
    )

    if status not in {200, 409}:
        print("Error:", status)
        print(json.dumps(payload, indent=2, ensure_ascii=False))
        return 1

    if status == 200:
        print("OK: compose generated")
        print("session_id:", payload.get("session_id"))
        print("repo_path:", payload.get("repo_path"))
        print("compose_path:", payload.get("compose_path"))
        return 0

    # Loop on 409 until resolved.
    state: dict[str, object] = {
        "fail_on_missing": True,
        "auto_assign_host_ports": True,
    }

    for _ in range(10):
        detail = payload.get("detail") if isinstance(payload, dict) else None
        if not isinstance(detail, dict):
            print("Invalid 409 response")
            print(json.dumps(payload, indent=2, ensure_ascii=False))
            return 1

        session_id = detail.get("session_id")
        if not session_id:
            print("Missing session_id in 409 detail")
            print(json.dumps(payload, indent=2, ensure_ascii=False))
            return 1

        missing_db = bool(detail.get("missing_db"))
        missing_env_vars = detail.get("missing_env_vars") or []
        missing = detail.get("missing") or []
        db_options = detail.get("db_options") or ["postgres", "mysql", "mongo", "redis"]

        # Strict-mode missing inputs (e.g., missing start command inference)
        if isinstance(missing, list) and missing:
            print("\nMissing Docker generation inputs (strict mode):")
            for item in missing:
                if not isinstance(item, dict):
                    continue
                svc = str(item.get("service") or "")
                reason = str(item.get("reason") or "")
                if svc:
                    print(f"- {svc}: {reason}")

            overrides = dict(state.get("service_start_cmd_overrides") or {})
            for item in missing:
                if not isinstance(item, dict):
                    continue
                svc = str(item.get("service") or "").strip()
                reason = str(item.get("reason") or "").lower()
                if not svc:
                    continue
                if svc in overrides:
                    continue
                if "start command" in reason or "could not be inferred" in reason:
                    raw = input(
                        f"Start command for service '{svc}' as JSON array (e.g. [\"npm\",\"start\"]): "
                    ).strip()
                    try:
                        parsed = json.loads(raw)
                    except Exception:
                        print("Invalid JSON. Aborting.")
                        return 1
                    if not isinstance(parsed, list) or not parsed or not all(isinstance(x, str) and x.strip() for x in parsed):
                        print("Invalid command: must be a non-empty JSON array of strings.")
                        return 1
                    overrides[svc] = [str(x) for x in parsed]

            if overrides:
                state["service_start_cmd_overrides"] = overrides

        if missing_db and "db" not in state:
            print("\nDB could not be inferred.")
            print("Options:", ", ".join(db_options), "or none")
            choice = input("DB type (or 'none'): ").strip().lower() or "none"
            if choice not in {"none", "no", "n"}:
                state["db"] = choice

        # Separate host port vars from secrets
        host_port_vars = [k for k in missing_env_vars if str(k).upper().endswith("_HOST_PORT")]
        other_vars = [k for k in missing_env_vars if k not in host_port_vars]

        if host_port_vars and not state.get("env_values"):
            print(f"\n{len(host_port_vars)} host ports are required.")
            auto = input("Auto-assign sequential host ports? [Y/n] ").strip().lower()
            if auto in {"n", "no"}:
                env_values: dict[str, str] = {}
                for k in host_port_vars:
                    v = input(f"{k}: ").strip()
                    env_values[str(k)] = v
                state["env_values"] = env_values
                state["auto_assign_host_ports"] = False
            else:
                base_port = input("Base host port [8100]: ").strip() or "8100"
                try:
                    state["host_port_base"] = int(base_port)
                except Exception:
                    print("Invalid base host port")
                    return 1

        if other_vars:
            print("\nMissing required environment variables:")
            env_values = dict(state.get("env_values") or {})
            for k in other_vars:
                kk = str(k)
                if kk in env_values:
                    continue
                if _is_secret_key(kk):
                    print(f"{kk} (secret):")
                v = input(f"{kk}: ").strip()
                env_values[kk] = v
            state["env_values"] = env_values

        status, payload = _post_json(f"{base}/continue/{session_id}", state)
        if status == 200:
            print("\nOK: compose generated")
            print("session_id:", payload.get("session_id"))
            print("repo_path:", payload.get("repo_path"))
            print("compose_path:", payload.get("compose_path"))
            print("missing_env_vars:", payload.get("missing_env_vars"))
            print("missing_db:", payload.get("missing_db"))
            return 0

        if status != 409:
            print("\nContinue failed:", status)
            print(json.dumps(payload, indent=2, ensure_ascii=False))
            return 1

    print("Too many missing-input rounds; aborting.")
    print(json.dumps(payload, indent=2, ensure_ascii=False))
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
