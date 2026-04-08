"""Simple in-memory cache with TTL"""

import time
from typing import Any, Dict, Optional
from threading import Lock


class Cache:
    """Thread-safe in-memory cache with TTL"""

    def __init__(self, ttl: int = 3600):
        self.ttl = ttl
        self._cache: Dict[str, tuple[Any, float]] = {}
        self._lock = Lock()

    def get(self, key: str) -> Optional[Any]:
        """Get value from cache if not expired"""
        with self._lock:
            if key in self._cache:
                value, timestamp = self._cache[key]
                if time.time() - timestamp < self.ttl:
                    return value
                else:
                    del self._cache[key]
            return None

    def set(self, key: str, value: Any) -> None:
        """Set value in cache with current timestamp"""
        with self._lock:
            self._cache[key] = (value, time.time())

    def clear(self) -> None:
        """Clear all cache entries"""
        with self._lock:
            self._cache.clear()

    def invalidate(self, key: str) -> None:
        """Remove specific key from cache"""
        with self._lock:
            if key in self._cache:
                del self._cache[key]