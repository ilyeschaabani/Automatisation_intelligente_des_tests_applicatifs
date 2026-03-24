"""Chunking engine for large files"""

from pathlib import Path
from typing import List, Dict, Any, Optional
from dataclasses import dataclass
import hashlib


@dataclass
class Chunk:
    """Represents a chunk of a file"""
    content: str
    start_line: int
    end_line: int
    context: Dict[str, Any]  # e.g., imports, class definitions


class ChunkingEngine:
    """Splits large files into manageable chunks for processing"""

    # Default maximum chunk size (lines)
    DEFAULT_MAX_LINES = 500

    # Overlap between chunks (lines)
    OVERLAP_LINES = 50

    def __init__(self, max_chunk_size: int = None):
        self.max_chunk_size = max_chunk_size or self.DEFAULT_MAX_LINES
        self.logger = None  # Will be set by caller if needed

    def chunk_file(self, file_path: Path, content: str = None) -> List[Chunk]:
        """
        Split file into chunks based on logical boundaries.

        Args:
            file_path: Path to file
            content: Optional file content (will read if not provided)

        Returns:
            List of Chunk objects
        """
        if content is None:
            content = file_path.read_text(encoding="utf-8", errors="ignore")

        lines = content.splitlines()

        if len(lines) <= self.max_chunk_size:
            # File is small enough, return as single chunk
            return [Chunk(
                content=content,
                start_line=1,
                end_line=len(lines),
                context={}
            )]

        # For larger files, split by function/class boundaries if possible
        chunks = self._split_by_logical_blocks(lines, file_path)

        # If we couldn't find good boundaries, use sliding window
        if not chunks:
            chunks = self._sliding_window_chunks(lines)

        return chunks

    def _split_by_logical_blocks(self, lines: List[str], file_path: Path) -> List[Chunk]:
        """
        Try to split by function/class definitions.

        Returns:
            List of Chunk objects or empty list if no good boundaries found
        """
        chunks = []
        current_chunk = []
        current_start = 1
        in_function = False
        indent_stack = []

        # Language-specific block starters
        block_starters = {
            "def ", "class ", "function ", "async def ", "export ", "const ", "let ",
            "var ", "interface ", "type ", "enum ", "@app.route", "@router", "@GetMapping"
        }

        for i, line in enumerate(lines, start=1):
            stripped = line.strip()

            # Check if line starts a new major block
            is_block_start = any(stripped.startswith(starter) for starter in block_starters)

            if is_block_start and len(current_chunk) > self.max_chunk_size // 2:
                # Save current chunk and start new one
                chunk_content = "\n".join(current_chunk)
                chunks.append(Chunk(
                    content=chunk_content,
                    start_line=current_start,
                    end_line=i - 1,
                    context={}
                ))
                current_chunk = [line]
                current_start = i
            else:
                current_chunk.append(line)

        # Add final chunk
        if current_chunk:
            chunk_content = "\n".join(current_chunk)
            chunks.append(Chunk(
                content=chunk_content,
                start_line=current_start,
                end_line=len(lines),
                context={}
            ))

        # Only use these chunks if they're reasonably sized
        if chunks and max(len(c.content.splitlines()) for c in chunks) <= self.max_chunk_size * 1.5:
            return chunks

        return []

    def _sliding_window_chunks(self, lines: List[str]) -> List[Chunk]:
        """
        Create overlapping sliding window chunks.

        Args:
            lines: List of file lines

        Returns:
            List of Chunk objects with overlap
        """
        chunks = []
        total_lines = len(lines)
        start = 0

        while start < total_lines:
            end = min(start + self.max_chunk_size, total_lines)

            # Check if we should extend to end of file
            if end < total_lines:
                # Try to end at a logical boundary (empty line or end of statement)
                for i in range(end, max(start, end - 50), -1):
                    if i < total_lines and (lines[i-1].strip() == "" or lines[i-1].strip().endswith(";")):
                        end = i
                        break

            chunk_lines = lines[start:end]
            chunk_content = "\n".join(chunk_lines)

            chunks.append(Chunk(
                content=chunk_content,
                start_line=start + 1,
                end_line=end,
                context={}
            ))

            # Move start position with overlap
            start = end - self.OVERLAP_LINES if end < total_lines else total_lines

        return chunks

    def chunk_with_context(self, file_path: Path, content: str = None) -> List[Chunk]:
        """
        Chunk file while preserving context like imports and global declarations.

        Args:
            file_path: Path to file
            content: Optional file content

        Returns:
            List of Chunk objects with context information
        """
        if content is None:
            content = file_path.read_text(encoding="utf-8", errors="ignore")

        lines = content.splitlines()

        # First, extract global context (imports, requires, etc.)
        global_context = self._extract_global_context(lines)

        # Then chunk the rest
        chunks = self.chunk_file(file_path, content)

        # Add global context to first chunk
        if chunks and global_context:
            chunks[0].context["imports"] = global_context.get("imports", [])
            chunks[0].context["requires"] = global_context.get("requires", [])

        return chunks

    def _extract_global_context(self, lines: List[str]) -> Dict[str, List[str]]:
        """Extract import statements and global declarations"""
        context = {"imports": [], "requires": []}

        for line in lines[:100]:  # Only check first 100 lines for global context
            stripped = line.strip()
            if stripped.startswith(("import ", "from ", "require(", "const ", "let ", "var ")):
                context["imports"].append(line)
            elif stripped.startswith(("require(", "module.exports")):
                context["requires"].append(line)

            # Stop at first non-import, non-empty line after imports
            if stripped and not any(stripped.startswith(p) for p in ["import", "from", "require", "const", "let", "var"]):
                if len(context["imports"]) > 0:
                    break

        return context

    def compute_chunk_hash(self, chunk: Chunk) -> str:
        """Compute hash for chunk content (for caching)"""
        return hashlib.sha256(chunk.content.encode("utf-8")).hexdigest()[:16]