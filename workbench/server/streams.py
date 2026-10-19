"""Long-lived Server-Sent Event loops for the Workbench gateway (Layer 4).

Kept apart from the request routing table because these handlers are the only
ones that hold a connection open for the duration of a sequencer run or a
Mantis reasoning loop, and their failure modes (client disconnect, idle
timeout) have nothing in common with ordinary request handling.
"""

import time
from typing import Any, Dict

from workbench.server.http_util import HttpResponder, read_json_body, require, single
from workbench.server.logger import SERVER_LOGGER

STREAM_POLL_SECONDS = 0.4
STREAM_IDLE_TIMEOUT_SECONDS = 1800


def stream_sequencer(context: Any, params: Dict[str, Any], responder: HttpResponder) -> None:
    """Contract 3: streams incremental sequencer output plus structured events."""
    session_id = require(params, "session_id")
    offset = int(single(params, "offset", "0") or 0)

    responder.begin_sse()
    deadline = time.time() + STREAM_IDLE_TIMEOUT_SECONDS

    while time.time() < deadline:
        chunk = context.runner.read_since(session_id, offset)
        offset = chunk["offset"]

        if chunk["text"]:
            if not responder.sse("log", {"offset": offset, "text": chunk["text"]}):
                return
        for event in chunk["events"]:
            if not responder.sse("test_event", event):
                return

        if not chunk["running"]:
            responder.sse("complete", {
                "session_id": session_id,
                "exit_code": chunk["exit_code"],
                "stopped": chunk["stopped"],
                "offset": offset,
            })
            return

        if not responder.sse("heartbeat", {"offset": offset}):
            return
        time.sleep(STREAM_POLL_SECONDS)

    responder.sse("error", {"message": "Stream idle timeout exceeded", "offset": offset})


def stream_mantis(context: Any, responder: HttpResponder) -> None:
    """Contract 2: streams the Mantis tripartite reasoning loop."""
    body = read_json_body(responder.handler)
    responder.begin_sse()
    try:
        for event in context.mantis_store.stream_chat_events(
            body, correlation_id=responder.correlation_id
        ):
            if not responder.sse(event["event"], event["data"]):
                return
    except Exception as exc:  # surfaced to the client, never silently swallowed
        SERVER_LOGGER.error(
            "MantisStreamAdapter",
            "sse.error",
            correlation_id=responder.correlation_id,
            layer="MANTIS_SSE",
            error={"code": exc.__class__.__name__, "message": str(exc)},
        )
        responder.sse("error", {"message": f"{exc.__class__.__name__}: {exc}"})
