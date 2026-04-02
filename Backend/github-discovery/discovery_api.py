import asyncio
import hashlib
import json
import uuid
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from src.config import Config
from src.contract_interview import build_findings, render_questionnaire
from src.contract_patch import apply_discovery_patch
from src.pipeline import Pipeline

app = FastAPI(title="Discovery Service")

jobs: Dict[str, Dict[str, Any]] = {}

def compute_repo_id(repo_url: str) -> str:
    return hashlib.sha256(repo_url.encode()).hexdigest()[:16]

class CreateJobIn(BaseModel):
    repo_url: str
    branch: Optional[str] = None
    keep_repo: bool = False


class AnswerIn(BaseModel):
    json_path: str
    value: Any


class CompleteJobIn(BaseModel):
    answers: List[AnswerIn]
    overwrite: bool = True
    return_openapi: bool = True


def _openapi_path_for_repo_id(cfg: Config, repo_id: str) -> Path:
    return Path(cfg.output_dir) / f"{repo_id}_openapi.json"


def _answers_to_merge_patch(answers: List[AnswerIn]) -> Dict[str, Any]:
    """Convert a list of $.x-discovery.* answers into a JSON Merge Patch object.

    For safety and simplicity, we only accept paths under $.x-discovery.
    """

    patch: Dict[str, Any] = {}

    for a in answers:
        jp = (a.json_path or "").strip()
        if not jp.startswith("$.x-discovery."):
            raise ValueError(f"Unsupported json_path (must start with $.x-discovery.): {jp}")

        # Strip prefix and split into keys
        suffix = jp[len("$.x-discovery.") :]
        if not suffix:
            raise ValueError(f"Invalid json_path: {jp}")

        keys = [k for k in suffix.split(".") if k]
        if not keys:
            raise ValueError(f"Invalid json_path: {jp}")

        # Normalize empty strings to None (so missing recompute stays accurate)
        value = a.value
        if isinstance(value, str) and value.strip() == "":
            value = None

        # Build nested dict under x-discovery
        cur = patch.setdefault("x-discovery", {})
        if not isinstance(cur, dict):
            raise ValueError("Patch collision at x-discovery")

        for k in keys[:-1]:
            nxt = cur.get(k)
            if not isinstance(nxt, dict):
                nxt = {}
                cur[k] = nxt
            cur = nxt

        cur[keys[-1]] = value

    return patch

@app.post("/discovery/jobs")
async def create_job(body: CreateJobIn):
    job_id = uuid.uuid4().hex
    repo_id = compute_repo_id(body.repo_url)

    jobs[job_id] = {
        "job_id": job_id,
        "repo_url": body.repo_url,
        "branch": body.branch,
        "repo_id": repo_id,
        "status": "queued",
        "created_at": datetime.utcnow().isoformat() + "Z",
        "error": None,
        "stats": None,
    }

    async def run_job():
        jobs[job_id]["status"] = "running"
        try:
            cfg = Config()
            pipeline = Pipeline(cfg)

            # exécution bloquante => thread
            result = await asyncio.to_thread(
                pipeline.run,
                body.repo_url,
                body.branch,
                body.keep_repo,
            )

            jobs[job_id]["status"] = "done"
            jobs[job_id]["stats"] = result.get("stats")
            # Align API-visible repo_id with the pipeline's repo_id to avoid output path mismatches.
            if result.get("repo_id"):
                jobs[job_id]["repo_id"] = result.get("repo_id")
        except Exception as e:
            jobs[job_id]["status"] = "error"
            jobs[job_id]["error"] = str(e)

    asyncio.create_task(run_job())
    return {"job_id": job_id, "repo_id": repo_id, "status": jobs[job_id]["status"]}

@app.get("/discovery/jobs/{job_id}")
def get_job(job_id: str):
    job = jobs.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Unknown job_id")
    return job

@app.get("/discovery/jobs/{job_id}/openapi")
def get_openapi(job_id: str):
    job = jobs.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Unknown job_id")
    if job["status"] != "done":
        raise HTTPException(status_code=409, detail=f"Job not done (status={job['status']})")

    cfg = Config()
    openapi_path = _openapi_path_for_repo_id(cfg, job["repo_id"])
    if not openapi_path.exists():
        raise HTTPException(status_code=404, detail="OpenAPI file not found")

    data = json.loads(openapi_path.read_text(encoding="utf-8"))
    return JSONResponse(content=data)

@app.get("/discovery/jobs/{job_id}/endpoints")
def get_endpoints(job_id: str):
    job = jobs.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Unknown job_id")
    if job["status"] != "done":
        raise HTTPException(status_code=409, detail=f"Job not done (status={job['status']})")

    cfg = Config()
    openapi_path = _openapi_path_for_repo_id(cfg, job["repo_id"])
    data = json.loads(openapi_path.read_text(encoding="utf-8"))

    out = []
    paths = data.get("paths") or {}
    for path, ops in paths.items():
        if not isinstance(ops, dict):
            continue
        for method, op in ops.items():
            if method.startswith("x-"):
                continue
            if not isinstance(op, dict):
                continue
            out.append({
                "method": method.upper(),
                "path": path,
                "summary": op.get("summary"),
                "tags": op.get("tags") or [],
                "source": op.get("x-source"),
                "confidence": op.get("x-confidence", 1.0),
                "request": (op.get("x-metadata") or {}).get("request"),
            })

    return {"repo_id": job["repo_id"], "count": len(out), "items": out}


@app.get("/discovery/jobs/{job_id}/contract/questionnaire")
def get_contract_questionnaire(job_id: str):
    """Return deterministic questions to make the generated OpenAPI runnable.

    A UI can call this after discovery completes, prompt the user, then POST answers
    back to `/complete`.
    """

    job = jobs.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Unknown job_id")
    if job["status"] != "done":
        raise HTTPException(status_code=409, detail=f"Job not done (status={job['status']})")

    cfg = Config()
    openapi_path = _openapi_path_for_repo_id(cfg, job["repo_id"])
    if not openapi_path.exists():
        raise HTTPException(status_code=404, detail="OpenAPI file not found")

    openapi_obj = json.loads(openapi_path.read_text(encoding="utf-8"))
    if not isinstance(openapi_obj, dict):
        raise HTTPException(status_code=500, detail="OpenAPI file is not a JSON object")

    findings = build_findings(openapi_obj)
    questionnaire = render_questionnaire(openapi_obj, findings)
    return {
        "repo_id": job["repo_id"],
        "findings": [f.to_dict() for f in findings],
        "questionnaire": questionnaire,
    }


@app.post("/discovery/jobs/{job_id}/complete")
def complete_discovery(job_id: str, body: CompleteJobIn):
    """Apply user-provided answers to x-discovery and rewrite the OpenAPI.

    This endpoint expects a list of answers as (json_path, value) pairs.
    The backend converts them to a JSON Merge Patch, applies it, aligns `servers[0].url`
    with `x-discovery.run.base_url`, recomputes `x-discovery.discovery.missing`, and
    overwrites the OpenAPI file.
    """

    job = jobs.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Unknown job_id")
    if job["status"] != "done":
        raise HTTPException(status_code=409, detail=f"Job not done (status={job['status']})")

    cfg = Config()
    openapi_path = _openapi_path_for_repo_id(cfg, job["repo_id"])
    if not openapi_path.exists():
        raise HTTPException(status_code=404, detail="OpenAPI file not found")

    try:
        openapi_obj = json.loads(openapi_path.read_text(encoding="utf-8"))
        if not isinstance(openapi_obj, dict):
            raise ValueError("OpenAPI file is not a JSON object")

        patch_obj = _answers_to_merge_patch(body.answers)
        updated = apply_discovery_patch(openapi_obj, patch_obj)

        if body.overwrite:
            openapi_path.write_text(json.dumps(updated, indent=2, ensure_ascii=False, default=str), encoding="utf-8")

        xdisc = updated.get("x-discovery") if isinstance(updated, dict) else None
        missing = []
        if isinstance(xdisc, dict):
            discovery = xdisc.get("discovery")
            if isinstance(discovery, dict):
                missing = discovery.get("missing") or []

        resp: Dict[str, Any] = {
            "repo_id": job["repo_id"],
            "applied_patch": patch_obj,
            "missing": missing,
        }
        if body.return_openapi:
            resp["openapi"] = updated
        return resp
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))