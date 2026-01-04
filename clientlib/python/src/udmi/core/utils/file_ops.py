"""
Common file operations utility for atomic and secure writes.
"""
import os
import tempfile
from typing import Any
from typing import List
from typing import Union


def atomic_write(path: str, data: Union[str, bytes], mode: int = 0o600) -> None:
    """
    Writes data to a file atomically and securely.

    1. Writes to a temporary file in the same directory (to ensure same filesystem).
    2. Flushes and fsyncs to ensure data is physically on disk.
    3. Sets file permissions (default 600).
    4. Atomically renames the temporary file to the target path.

    Args:
        path: The target file path.
        data: The content to write (str or bytes).
        mode: The file permissions (default 0o600 - Owner Read/Write).
    """
    directory = os.path.dirname(path) or "."

    if not os.path.exists(directory):
        os.makedirs(directory, exist_ok=True)

    open_mode = "w" if isinstance(data, str) else "wb"

    with tempfile.NamedTemporaryFile(mode=open_mode, dir=directory,
                                     delete=False) as tmp_file:
        try:
            tmp_file.write(data)
            tmp_file.flush()
            os.fsync(tmp_file.fileno())

            os.chmod(tmp_file.name, mode)

            tmp_file.close()

            os.replace(tmp_file.name, path)
        except Exception:
            tmp_file.close()
            if os.path.exists(tmp_file.name):
                os.unlink(tmp_file.name)
            raise


def mask_secrets(data: Any, sensitive_keys: List[str] = None) -> Any:
    """
    Recursively masks values for keys that match sensitive terms.
    Returns a deep copy of the data with secrets masked.

    Args:
        data: The input dictionary or list to sanitise.
        sensitive_keys: List of key names to mask (default: password, secret, key, token).
    """
    if sensitive_keys is None:
        sensitive_keys = ["password", "secret", "key_data", "private_key",
                          "token", "auth"]

    if isinstance(data, dict):
        new_data = {}
        for k, v in data.items():
            is_sensitive = any(s in k.lower() for s in sensitive_keys)

            if is_sensitive:
                new_data[k] = "********"
            else:
                new_data[k] = mask_secrets(v, sensitive_keys)
        return new_data
    elif isinstance(data, list):
        return [mask_secrets(item, sensitive_keys) for item in data]
    else:
        return data
