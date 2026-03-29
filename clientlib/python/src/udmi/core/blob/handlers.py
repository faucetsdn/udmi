"""
Definitions for Blob processing handlers.

This module defines the types and structures used to register callbacks
for handling specific blob updates (e.g., firmware updates, config reloading).
"""
from dataclasses import dataclass
from typing import Any
from typing import Callable
from typing import Optional
from typing import Union

# Handlers can accept:
# 1. str: A file path to the downloaded blob (preferred for large files)
# 2. bytes: The raw data content (small files)
BlobData = Union[str, bytes]

# Handler can accept bytes (small blobs) or str (filepath for large blobs)
# Signature: (blob_id: str, data: BlobData) -> Any
ProcessHandler = Callable[[str, BlobData], Any]

# Post-Process: Accepts input as output from Process, performs final action (e.g. Restart)
# Signature: (blob_id: str, context: Any) -> None
PostProcessHandler = Callable[[str, Any], None]


@dataclass
class BlobPipelineHandlers:
    """
    Container for the two-stage blob update pipeline.

    Attributes:
        process: Callback to handle the blob content (apply update, parse config).
        post_process: Callback to perform cleanup or restart after processing.
        expects_file: If True, the fetcher will download to disk and pass a path
                      to the 'process' handler. If False, passes raw bytes.
    """
    process: Optional[ProcessHandler] = None
    post_process: Optional[PostProcessHandler] = None
    expects_file: bool = False
