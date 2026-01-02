from typing import Any
from typing import Callable
from typing import NamedTuple
from typing import Optional
from typing import Union

# Handlers can accept:
# 1. str: A file path to the downloaded blob (preferred for large files)
# 2. bytes: The raw data content (small files)
BlobData = Union[str, bytes]

# Handler can accept bytes (small blobs) or str (filepath for large blobs)
ProcessHandler = Callable[[str, BlobData], Any]

# Post-Process: Accepts input as output from Process, performs final action (e.g. Restart)
PostProcessHandler = Callable[[str, Any], None]


class BlobPipelineHandlers(NamedTuple):
    """
    Container for the two-stage blob update pipeline.
    """
    process: ProcessHandler = None
    post_process: Optional[PostProcessHandler] = None
    expects_file: bool = False