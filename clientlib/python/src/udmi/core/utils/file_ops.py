"""
Common file operations utility for atomic and secure writes.

This module provides helpers for writing files in a way that prevents data
corruption during power failures (atomic_write) and for sanitizing logs (mask_secrets).
"""
import contextlib
import os
import tempfile
from typing import Any
from typing import Dict
from typing import Iterable
from typing import IO
from typing import Union


@contextlib.contextmanager
def atomic_file_context(path: str, mode: int = 0o600, open_mode: str = "wb") -> Iterable[IO]:
    """
    Context manager that creates a temporary file in the destination directory.

    On success (exiting context), it atomically moves the temp file to the target path.
    On failure (exception), it cleans up the temp file.

    Args:
        path: The target file path.
        mode: File permissions to apply (default 0o600).
        open_mode: Mode to open the file with (default "wb").
    """
    directory = os.path.dirname(path) or "."
    os.makedirs(directory, exist_ok=True)

    # Create temp file in the same directory to allow atomic rename
    with tempfile.NamedTemporaryFile(mode=open_mode, dir=directory, delete=False) as tmp_file:
        tmp_name = tmp_file.name
        try:
            yield tmp_file

            # Flush and sync to disk
            tmp_file.flush()
            os.fsync(tmp_file.fileno())

            # Apply permissions
            os.chmod(tmp_name, mode)

            # Close before moving
            tmp_file.close()

            # Atomic replace
            os.replace(tmp_name, path)

        except Exception:
            # Cleanup on failure
            tmp_file.close()
            if os.path.exists(tmp_name):
                os.unlink(tmp_name)
            raise


def atomic_write(path: str, data: Union[str, bytes], mode: int = 0o600) -> None:
    """
    Writes data to a file atomically and securely.
    """
    open_mode = "w" if isinstance(data, str) else "wb"

    with atomic_file_context(path, mode=mode, open_mode=open_mode) as f:
        f.write(data)


def mask_secrets(data: Any, sensitive_keys: Iterable[str] = None) -> Any:
    """
    Recursively masks values for keys that match sensitive terms.
    Returns a deep copy of the data with secrets masked.

    Args:
        data: The input dictionary or list to sanitize.
        sensitive_keys: Iterable of key names to mask.
                        Defaults to: ["password", "secret", "key_data",
                        "private_key", "token", "auth"]

    Returns:
        A new structure with sensitive values replaced by '********'.
    """
    if sensitive_keys is None:
        sensitive_keys = {"password", "secret", "key_data", "private_key",
                          "token", "auth"}
    else:
        sensitive_keys = set(sensitive_keys)

    if isinstance(data, dict):
        new_data: Dict[str, Any] = {}
        for k, v in data.items():
            # Check if key contains any sensitive term (case-insensitive substring match)
            is_sensitive = any(s in k.lower() for s in sensitive_keys)

            if is_sensitive:
                new_data[k] = "********"
            else:
                new_data[k] = mask_secrets(v, sensitive_keys)
        return new_data

    if isinstance(data, list):
        return [mask_secrets(item, sensitive_keys) for item in data]

    return data
