from pathlib import Path

from src.env_runner import generate_compose_draft


def test_generate_compose_draft_from_dockerfile_expose(tmp_path: Path):
    repo = tmp_path / "repo"
    repo.mkdir()

    dockerfile = repo / "Dockerfile"
    dockerfile.write_text(
        """FROM python:3.11-slim
EXPOSE 8080
CMD [\"python\", \"-m\", \"http.server\", \"8080\"]
""",
        encoding="utf-8",
    )

    out_dir = tmp_path / "out"
    res = generate_compose_draft(repo_root=repo, output_dir=out_dir)

    assert res.status == "stopped"
    assert res.compose_file is not None
    assert Path(res.compose_file).exists()
    assert res.detected_base_url == "http://localhost:8080"


def test_generate_compose_draft_multiple_dockerfiles_needs_input(tmp_path: Path):
    repo = tmp_path / "repo"
    repo.mkdir()

    (repo / "Dockerfile").write_text("FROM alpine\n", encoding="utf-8")
    sub = repo / "service"
    sub.mkdir()
    (sub / "Dockerfile").write_text("FROM alpine\n", encoding="utf-8")

    out_dir = tmp_path / "out"
    res = generate_compose_draft(repo_root=repo, output_dir=out_dir)

    assert res.status == "needs_user_input"
    assert res.questions and res.questions[0]["id"] == "dockerfile_path"
