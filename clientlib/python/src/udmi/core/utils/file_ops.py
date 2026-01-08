"""
Common file operations utility for atomic and secure writes.

This module provides helpers for writing files in a way that prevents data
corruption during power failures (atomic_write) and for sanitizing logs (mask_secrets).
"""
import os
import tempfile
from typing import Any
from typing import Dict
from typing import Iterable
from typing import Union


def atomic_write(path: str, data: Union[str, bytes], mode: int = 0o600) -> None:
    """
    Writes data to a file atomically and securely.

    This function prevents partial writes (corruption) by:
    1. Writing to a temporary file in the same directory (ensuring same filesystem).
    2. Flushing and fsyncing to ensure data is physically on disk.
    3. Setting file permissions (default 600: Owner Read/Write).
    4. Atomically renaming the temporary file to the target path.

    Args:
        path: The target file path.
        data: The content to write (str or bytes).
        mode: The file permissions (default 0o600).
    """
    directory = os.path.dirname(path) or "."

    # Ensure target directory exists
    os.makedirs(directory, exist_ok=True)

    open_mode = "w" if isinstance(data, str) else "wb"

    # Create temp file in the same directory to allow atomic rename
    with tempfile.NamedTemporaryFile(mode=open_mode, dir=directory,
                                     delete=False) as tmp_file:
        tmp_name = tmp_file.name
        try:
            tmp_file.write(data)
            tmp_file.flush()
            os.fsync(tmp_file.fileno())  # Force write to physical disk

            # Apply permissions before moving into place
            os.chmod(tmp_name, mode)

            # Close the file descriptor before moving
            tmp_file.close()

            # Atomic replace
            os.replace(tmp_name, path)
        except Exception:
            # Cleanup on failure
            tmp_file.close()
            if os.path.exists(tmp_name):
                os.unlink(tmp_name)
            raise


def mask_secrets(data: Any, sensitive_keys: Iterable[str] = None) -> Any:
    """
    Recursively masks values for keys that match sensitive terms.
    Returns a deep copy of the data with secrets masked.

    Args:
        data: The input dictionary or list to sanitize.
        sensitive_keys: Iterable of key names to mask.
                        Defaults to: ["password", "secret", "key_data", "private_key", "token", "auth"]

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
