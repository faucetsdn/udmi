from typing import Any
from typing import Callable
from typing import NamedTuple
from typing import Optional

# Process: Accepts verified data bytes
ProcessHandler = Callable[[str, bytes], Any]

# Post-Process: Accepts input as output from Process, performs final action (e.g. Restart)
PostProcessHandler = Callable[[str, Any], None]


class BlobPipelineHandlers(NamedTuple):
    """
    Container for the two-stage blob update pipeline.
    """
    process: ProcessHandler = None
    post_process: Optional[PostProcessHandler] = None