from __future__ import annotations

import re
import unicodedata
import uuid
from pathlib import Path

_INVALID_WINDOWS_CHARS = re.compile(r'[<>:"/\\|?*\x00-\x1f]')
_RESERVED_WINDOWS_NAMES = {
    "CON",
    "PRN",
    "AUX",
    "NUL",
    *(f"COM{number}" for number in range(1, 10)),
    *(f"LPT{number}" for number in range(1, 10)),
}


def sanitize_filename(name: str, *, max_length: int = 180) -> str:
    """Return a safe filename component that works on Windows and Unix."""
    normalized = unicodedata.normalize("NFKC", Path(name).name)
    cleaned = _INVALID_WINDOWS_CHARS.sub("_", normalized)
    cleaned = re.sub(r"\s+", " ", cleaned).strip(" .")
    if not cleaned:
        cleaned = "download"
    path = Path(cleaned)
    stem = path.stem.strip(" .") or "download"
    suffix = path.suffix[:16]
    if stem.upper() in _RESERVED_WINDOWS_NAMES:
        stem = f"_{stem}"
    available = max(max_length - len(suffix), 1)
    return f"{stem[:available].rstrip(' .')}{suffix}"


def unique_destination(directory: Path, unsafe_name: str) -> Path:
    """Choose a safe destination without overwriting an existing file."""
    directory = directory.resolve()
    candidate = directory / sanitize_filename(unsafe_name)
    if candidate.parent != directory:
        raise ValueError("Destination escaped the configured download directory")
    if not candidate.exists():
        return candidate
    suffix = uuid.uuid4().hex[:8]
    return candidate.with_name(f"{candidate.stem}-{suffix}{candidate.suffix}")
