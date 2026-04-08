from __future__ import annotations

import dataclasses
import pathlib
from typing import Optional

from .analyze import ProjectAnalysis, analyze_project
from .generate import ComposeArtifacts, render_and_write_artifacts, render_compose_yaml_preview
from .git_ops import ensure_local_checkout
from .ollama_client import maybe_refine_with_ollama


@dataclasses.dataclass(frozen=True)
class GenerateResult:
    project_dir: pathlib.Path
    compose_path: pathlib.Path
    dockerfile_path: Optional[pathlib.Path]
    dockerfile_paths: list[pathlib.Path]
    env_example_path: Optional[pathlib.Path]
    detected_db_types: list[str]
    notes: list[str]


def generate_compose_for_target(
    *,
    target: str,
    branch: Optional[str],
    out_dir: Optional[pathlib.Path],
    in_place: bool,
    use_ollama: bool,
    ollama_model: str,
    forced_db: Optional[str],
    forced_port: Optional[int],
    forced_health_path: Optional[str],
    service_port_overrides: Optional[dict[str, int]] = None,
    write_dockerfiles_in_repo: bool = False,
) -> GenerateResult:
    project_dir = ensure_local_checkout(target=target, branch=branch)

    analysis: ProjectAnalysis = analyze_project(
        project_dir,
        forced_db=forced_db,
        forced_port=forced_port,
        forced_health_path=forced_health_path,
    )

    artifacts: ComposeArtifacts = analysis.to_artifacts()
    if service_port_overrides:
        artifacts = _apply_service_port_overrides(artifacts, service_port_overrides)

    notes: list[str] = []
    if use_ollama:
        compose_preview = render_compose_yaml_preview(artifacts=artifacts, base_dir=project_dir)
        refined = maybe_refine_with_ollama(
            analysis=analysis,
            draft=artifacts,
            model=ollama_model,
            compose_yaml_preview=compose_preview,
        )
        if refined is not None:
            artifacts = refined
            notes.append("Ollama: compose draft refined.")
        else:
            notes.append("Ollama: not available or refinement failed; used heuristic draft.")

    output_dir = project_dir if (in_place or out_dir is None) else out_dir
    output_dir.mkdir(parents=True, exist_ok=True)

    # Dockerfiles must live inside each service build context.
    # This generator no longer clones git URLs itself; therefore, writing Dockerfiles inside the repo
    # is allowed only when explicitly requested or when generating in-place.
    can_write_dockerfiles = in_place or write_dockerfiles_in_repo

    detected_db_types = sorted({s.db for s in analysis.services if s.db})

    written = render_and_write_artifacts(
        output_dir=output_dir,
        analysis=analysis,
        artifacts=artifacts,
        can_write_dockerfiles=can_write_dockerfiles,
    )
    notes.extend(written.notes)

    return GenerateResult(
        project_dir=project_dir,
        compose_path=written.compose_path,
        dockerfile_path=written.dockerfile_paths[0] if written.dockerfile_paths else None,
        dockerfile_paths=written.dockerfile_paths,
        env_example_path=written.env_example_path,
        detected_db_types=detected_db_types,
        notes=notes,
    )


def _apply_service_port_overrides(
    artifacts: ComposeArtifacts, overrides: dict[str, int]
) -> ComposeArtifacts:
    updated = []
    for s in artifacts.services:
        new_port = overrides.get(s.name)
        if isinstance(new_port, int) and 1 <= new_port <= 65535:
            updated.append(
                type(s)(
                    name=s.name,
                    service_dir=s.service_dir,
                    project_kind=s.project_kind,
                    runtime=s.runtime,
                    port=new_port,
                    db=s.db,
                    health_path=s.health_path,
                    dockerfile=s.dockerfile,
                )
            )
        else:
            updated.append(s)
    return ComposeArtifacts(project_dir=artifacts.project_dir, services=updated)
