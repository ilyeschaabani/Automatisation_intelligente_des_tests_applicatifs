"""Repository cloning and management"""

import hashlib
import shutil
import stat
import time
import uuid
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

    def _rmtree_with_retries(self, path: Path, attempts: int = 5, delay_seconds: float = 0.5) -> None:
        """Remove a directory tree with retries (helps on Windows file locking)."""

        def _onerror(func, p, exc_info):
            try:
                os.chmod(p, stat.S_IWRITE)
                func(p)
            except Exception:
                raise

        import os

        last_error: Optional[Exception] = None
        for attempt in range(1, attempts + 1):
            try:
                if not path.exists():
                    return
                shutil.rmtree(path, onerror=_onerror)
                return
            except Exception as e:
                last_error = e
                # Give the OS/AV/indexer a moment to release locks
                time.sleep(delay_seconds * attempt)

        if last_error:
            raise last_error

    def clone_repository(self, url: str, branch: Optional[str] = None, keep_repo: bool = False) -> Tuple[Path, bool, str]:
        """
        Clone repository with retry logic and caching.

        Args:
            url: GitHub repository URL
            branch: Optional branch to clone

        Returns:
            Tuple of (repo_path, was_cached, repo_id)

        Raises:
            CloneError: If cloning fails after retries
        """
        repo_id = self._generate_repo_id(url)
        # If we're keeping the repo, clone into a stable folder for debugging.
        # Otherwise, use a unique temp folder for this run to avoid collisions and stale partial clones.
        if keep_repo:
            repo_path = self.repos_dir / repo_id
        else:
            repo_path = self.repos_dir / f"{repo_id}_tmp_{uuid.uuid4().hex[:8]}"

        # Check cache first
        cache_key = f"repo_clone:{url}"
        if keep_repo and repo_path.exists() and self.cache.get(cache_key):
            self.logger.info("Using cached repository", url=url, path=str(repo_path))
            return repo_path, True, repo_id

        # Clean up any partial clone
        if repo_path.exists():
            self.logger.warning("Cleaning up incomplete clone", path=str(repo_path))
            try:
                self._rmtree_with_retries(repo_path)
            except Exception as cleanup_err:
                self.logger.warning(
                    "Failed to fully cleanup incomplete clone",
                    path=str(repo_path),
                    error=str(cleanup_err)
                )
                # If we cannot clean up a temp target, pick a new one.
                if not keep_repo:
                    repo_path = self.repos_dir / f"{repo_id}_tmp_{uuid.uuid4().hex[:8]}"

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
                if keep_repo:
                    self.cache.set(cache_key, True)
                self.logger.info("Repository cloned successfully", url=url, path=str(repo_path))
                return repo_path, False, repo_id

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
                        try:
                            self._rmtree_with_retries(repo_path)
                        except Exception:
                            # If cleanup fails for a temp target, try a new temp folder next attempt
                            if not keep_repo:
                                repo_path = self.repos_dir / f"{repo_id}_tmp_{uuid.uuid4().hex[:8]}"
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
            self._rmtree_with_retries(repo_path)
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