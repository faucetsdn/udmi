"""
Common file operations utility for atomic and secure writes.
"""
import os
import tempfile
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
