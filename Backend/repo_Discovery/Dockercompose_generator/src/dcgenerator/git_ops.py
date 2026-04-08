from __future__ import annotations

import pathlib
import re
from typing import Optional


def is_git_url(target: str) -> bool:
    return bool(re.match(r"^(https?://|git@|ssh://)", target)) or target.endswith(".git")


def ensure_local_checkout(*, target: str, branch: Optional[str]) -> pathlib.Path:
    """Return a local folder for the target.

    This project no longer clones git URLs itself.

    - If target is a local path, returns it (resolved).
    - If target looks like a git URL (or doesn't exist), raises a ValueError.

    Rationale: cloning is delegated to an external "clone" service (e.g. clone_repo)
    which can manage temp checkouts and cleanup.
    """

    if branch is not None:
        # Keeping the parameter for backward compatibility, but this module does not manage git state anymore.
        raise ValueError("branch checkout is not supported here; checkout in the clone service")

    path = pathlib.Path(target)
    if path.exists():
        return path.resolve()

    if is_git_url(target):
        raise ValueError(
            "git URL targets are not supported (cloning disabled). "
            "Clone the repository first (e.g. using clone_repo) and pass the local path instead."
        )

    raise ValueError(f"Target path does not exist: {target}")
