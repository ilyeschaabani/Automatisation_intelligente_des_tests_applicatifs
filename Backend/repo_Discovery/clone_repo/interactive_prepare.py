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

    if status == 200:
        print("OK: compose generated")
        print("session_id:", payload.get("session_id"))
        print("repo_path:", payload.get("repo_path"))
        print("compose_path:", payload.get("compose_path"))
        return 0

    if status != 409:
        print("Error:", status)
        print(json.dumps(payload, indent=2, ensure_ascii=False))
        return 1

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
    db_options = detail.get("db_options") or ["postgres", "mysql", "mongo", "redis"]

    answers: dict[str, object] = {
        "fail_on_missing": True,
        "auto_assign_host_ports": True,
    }

    if missing_db:
        print("\nDB could not be inferred.")
        print("Options:", ", ".join(db_options), "or none")
        choice = input("DB type (or 'none'): ").strip().lower() or "none"
        if choice not in {"none", "no", "n"}:
            answers["db"] = choice

    # Separate host port vars from secrets
    host_port_vars = [k for k in missing_env_vars if str(k).upper().endswith("_HOST_PORT")]
    other_vars = [k for k in missing_env_vars if k not in host_port_vars]

    if host_port_vars:
        print(f"\n{len(host_port_vars)} host ports are required.")
        auto = input("Auto-assign sequential host ports? [Y/n] ").strip().lower()
        if auto in {"n", "no"}:
            env_values: dict[str, str] = {}
            for k in host_port_vars:
                v = input(f"{k}: ").strip()
                env_values[k] = v
            answers["env_values"] = env_values
            answers["auto_assign_host_ports"] = False
        else:
            base_port = input("Base host port [8100]: ").strip() or "8100"
            try:
                answers["host_port_base"] = int(base_port)
            except Exception:
                print("Invalid base host port")
                return 1

    if other_vars:
        print("\nMissing required environment variables:")
        env_values = dict(answers.get("env_values") or {})
        for k in other_vars:
            if _is_secret_key(str(k)):
                # no hidden input with stdlib; just warn
                print(f"{k} (secret):")
            v = input(f"{k}: ").strip()
            env_values[str(k)] = v
        answers["env_values"] = env_values

    # Second call: continue the same session
    status2, payload2 = _post_json(f"{base}/continue/{session_id}", answers)

    if status2 == 200:
        print("\nOK: compose generated")
        print("session_id:", payload2.get("session_id"))
        print("repo_path:", payload2.get("repo_path"))
        print("compose_path:", payload2.get("compose_path"))
        print("missing_env_vars:", payload2.get("missing_env_vars"))
        print("missing_db:", payload2.get("missing_db"))
        return 0

    print("\nContinue failed:", status2)
    print(json.dumps(payload2, indent=2, ensure_ascii=False))
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
