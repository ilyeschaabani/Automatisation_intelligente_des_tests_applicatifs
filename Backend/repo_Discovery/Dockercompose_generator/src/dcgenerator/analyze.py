from __future__ import annotations

import dataclasses
import pathlib
import re
from typing import Optional

from .detect import (
    detect_db,
    detect_health_path,
    detect_java_build_tool,
    detect_java_version,
    detect_node_start,
    detect_project_kind,
    detect_python_start,
    detect_runtime_and_port,
    find_service_roots,
)
from .generate import ComposeArtifacts, DockerfilePlan, ServiceArtifacts


@dataclasses.dataclass(frozen=True)
class ServiceAnalysis:
    name: str
    service_dir: pathlib.Path
    project_kind: str
    runtime: str
    port: Optional[int]
    db: Optional[str]
    health_path: Optional[str]
    has_dockerfile: bool


@dataclasses.dataclass(frozen=True)
class ProjectAnalysis:
    project_dir: pathlib.Path
    services: list[ServiceAnalysis]

    def to_artifacts(self) -> ComposeArtifacts:
        service_artifacts: list[ServiceArtifacts] = []
        for s in self.services:
            dockerfile = None
            if not s.has_dockerfile and s.port is not None:
                start_cmd = None
                extra_env = None
                java_build_tool = None
                java_version = None
                if s.runtime == "python":
                    start = detect_python_start(s.service_dir, port=s.port)
                    start_cmd = start.start_cmd
                    extra_env = start.extra_env
                elif s.runtime == "node":
                    start_cmd = detect_node_start(s.service_dir)
                elif s.runtime == "java":
                    java_build_tool = detect_java_build_tool(s.service_dir)
                    java_version = detect_java_version(s.service_dir)

                dockerfile = DockerfilePlan(
                    runtime=s.runtime,
                    port=s.port,
                    start_cmd=start_cmd,
                    extra_env=extra_env,
                    java_build_tool=java_build_tool,
                    java_version=java_version,
                )

            service_artifacts.append(
                ServiceArtifacts(
                    name=s.name,
                    service_dir=s.service_dir,
                    project_kind=s.project_kind,
                    runtime=s.runtime,
                    port=s.port,
                    db=s.db,
                    health_path=s.health_path,
                    dockerfile=dockerfile,
                )
            )

        return ComposeArtifacts(project_dir=self.project_dir, services=service_artifacts)


def analyze_project(
    project_dir: pathlib.Path,
    *,
    forced_db: Optional[str],
    forced_port: Optional[int],
    forced_health_path: Optional[str],
) -> ProjectAnalysis:
    project_dir = project_dir.resolve()

    roots = find_service_roots(project_dir)
    services: list[ServiceAnalysis] = []

    for root in roots:
        name = root.name if root != project_dir else "app"
        project_kind = detect_project_kind(root)
        runtime, detected_port = detect_runtime_and_port(root, project_kind)

        # port: allow global forced_port only for single-service repos
        port: Optional[int]
        if forced_port is not None and len(roots) == 1:
            port = forced_port
        else:
            port = detected_port

        db = forced_db if forced_db is not None else detect_db(root, project_kind)

        if forced_health_path is not None and len(roots) == 1:
            health_path = forced_health_path
        else:
            health_path = detect_health_path(root)

        if health_path is not None:
            health_path = health_path.strip()
            if not health_path.startswith("/"):
                health_path = "/" + health_path
            health_path = re.sub(r"\s+", "", health_path)

        has_dockerfile = (root / "Dockerfile").exists()

        services.append(
            ServiceAnalysis(
                name=name,
                service_dir=root,
                project_kind=project_kind,
                runtime=runtime,
                port=port,
                db=db,
                health_path=health_path,
                has_dockerfile=has_dockerfile,
            )
        )

    return ProjectAnalysis(project_dir=project_dir, services=services)
