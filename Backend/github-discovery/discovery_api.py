import asyncio
import hashlib
import json
import uuid
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, Optional

from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from src.config import Config
from src.pipeline import Pipeline

app = FastAPI(title="Discovery Service")

jobs: Dict[str, Dict[str, Any]] = {}

def compute_repo_id(repo_url: str) -> str:
    return hashlib.sha256(repo_url.encode()).hexdigest()[:16]

class CreateJobIn(BaseModel):
    repo_url: str
    branch: Optional[str] = None
    keep_repo: bool = False

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
    openapi_path = Path(cfg.output_dir) / f"{job['repo_id']}_openapi.json"
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
    openapi_path = Path(cfg.output_dir) / f"{job['repo_id']}_openapi.json"
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