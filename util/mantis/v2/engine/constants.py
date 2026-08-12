"""
Mantis Engine Constants & Workspace Utilities.
"""

import os
from pathlib import Path
from typing import Optional

DEFAULT_GEMINI_PRO_MODEL = "gemini-3.1-pro-preview"
DEFAULT_GEMINI_FLASH_MODEL = "gemini-2.5-flash"
DEFAULT_GEMINI_FLASH_LITE_MODEL = "gemini-2.5-flash-lite"

DEFAULT_MAX_LOOPS = 15
DEFAULT_MAX_REVISIONS = 10
DEFAULT_CHUNK_SIZE = 2500
DEFAULT_CONCURRENCY_LIMIT = 3


def get_udmi_root(start_path: Optional[str] = None) -> str:
    """
    Locates the UDMI project root by searching upwards from start_path (or current file/cwd)
    for indicator files like 'schema', 'validator', 'bin/mantis', or 'bin/run_tests'.
    """
    if "UDMI_ROOT" in os.environ and os.path.exists(os.environ["UDMI_ROOT"]):
        return os.path.abspath(os.environ["UDMI_ROOT"])

    cur = Path(start_path or os.getcwd()).resolve()
    for p in [cur] + list(cur.parents):
        if (p / "schema").is_dir() and (p / "validator").is_dir():
            return str(p)
        if (p / "bin" / "mantis").is_file():
            return str(p)

    # Fallback based on relative file location (src/engine/constants.py -> util/mantis/src/engine -> 4 levels up)
    fallback = Path(__file__).resolve().parents[4]
    return str(fallback)