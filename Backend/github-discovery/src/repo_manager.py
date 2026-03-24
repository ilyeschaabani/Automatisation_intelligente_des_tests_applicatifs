"""Repository cloning and management"""

import hashlib
import shutil
from pathlib import Path
from typing import Optional, Tuple
import git
from git import Repo

from .config import Config
from .utils.logger import get_logger
from .utils.errors import CloneError, RepositoryError
from .utils.cache import Cache


class RepositoryManager:
    """Manages repository cloning, caching, and cleanup"""

    def __init__(self, config: Config, cache: Cache):
        self.config = config
        self.cache = cache
        self.logger = get_logger(__name__)
        self.repos_dir = Path(config.repos_dir)
        self.repos_dir.mkdir(parents=True, exist_ok=True)

    def _generate_repo_id(self, url: str) -> str:
        """Generate unique ID for repository URL"""
        return hashlib.sha256(url.encode()).hexdigest()[:16]

    def clone_repository(self, url: str, branch: Optional[str] = None) -> Tuple[Path, bool]:
        """
        Clone repository with retry logic and caching.

        Args:
            url: GitHub repository URL
            branch: Optional branch to clone

        Returns:
            Tuple of (repo_path, was_cached)

        Raises:
            CloneError: If cloning fails after retries
        """
        repo_id = self._generate_repo_id(url)
        repo_path = self.repos_dir / repo_id

        # Check cache first
        cache_key = f"repo_clone:{url}"
        if repo_path.exists() and self.cache.get(cache_key):
            self.logger.info("Using cached repository", url=url, path=str(repo_path))
            return repo_path, True

        # Clean up any partial clone
        if repo_path.exists():
            self.logger.warning("Cleaning up incomplete clone", path=str(repo_path))
            try:
                shutil.rmtree(repo_path)
            except PermissionError as e:
                # Windows file locking issue - try harder to remove
                import time
                import gc
                gc.collect()  # Force garbage collection to release file handles
                time.sleep(0.5)  # Small delay for OS to release locks
                try:
                    shutil.rmtree(repo_path, ignore_errors=True)
                except Exception as cleanup_err:
                    self.logger.warning(
                        "Failed to fully cleanup incomplete clone, continuing anyway",
                        path=str(repo_path),
                        error=str(cleanup_err)
                    )

        # Clone with retries
        for attempt in range(1, self.config.git_retries + 1):
            try:
                self.logger.info(
                    "Cloning repository",
                    url=url,
                    attempt=attempt,
                    max_attempts=self.config.git_retries
                )

                clone_kwargs = {
                    "to_path": str(repo_path),
                    "multi_options": ["--no-tags", "--depth=1"],  # Shallow clone
                }

                if branch:
                    clone_kwargs["branch"] = branch
                    clone_kwargs["multi_options"].append("--no-single-branch")

                repo = git.Repo.clone_from(url, **clone_kwargs)

                # Cache the successful clone
                self.cache.set(cache_key, True)
                self.logger.info("Repository cloned successfully", url=url, path=str(repo_path))
                return repo_path, False

            except git.GitCommandError as e:
                self.logger.warning(
                    "Clone attempt failed",
                    url=url,
                    attempt=attempt,
                    error=str(e)
                )

                if attempt < self.config.git_retries:
                    # Clean up before retry
                    if repo_path.exists():
                        shutil.rmtree(repo_path)
                else:
                    raise CloneError(
                        f"Failed to clone repository after {self.config.git_retries} attempts",
                        details={"url": url, "final_error": str(e)}
                    ) from e

            except Exception as e:
                raise CloneError(
                    f"Unexpected error during clone: {str(e)}",
                    details={"url": url}
                ) from e

    def cleanup_repository(self, repo_path: Path, keep: bool = False) -> None:
        """
        Clean up cloned repository.

        Args:
            repo_path: Path to repository
            keep: If True, keep the repository (for debugging)
        """
        if not repo_path.exists():
            return

        if keep:
            self.logger.info("Keeping repository for debugging", path=str(repo_path))
            return

        try:
            shutil.rmtree(repo_path)
            self.logger.debug("Repository cleaned up", path=str(repo_path))
        except Exception as e:
            self.logger.warning("Failed to cleanup repository", path=str(repo_path), error=str(e))

    def get_repository_info(self, repo_path: Path) -> dict:
        """
        Get basic repository information.

        Args:
            repo_path: Path to cloned repository

        Returns:
            Dictionary with repo info
        """
        try:
            repo = Repo(repo_path)
            info = {
                "active_branch": repo.active_branch.name,
                "commit_hash": repo.head.commit.hexsha,
                "commit_message": repo.head.commit.message.strip(),
                "author": str(repo.head.commit.author),
                "committed_date": repo.head.commit.committed_datetime.isoformat(),
                "size_mb": sum(f.stat().st_size for f in repo_path.rglob("*") if f.is_file()) / (1024*1024),
            }
            return info
        except Exception as e:
            self.logger.warning("Failed to get repository info", error=str(e))
            return {}