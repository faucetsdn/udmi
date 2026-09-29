"""Contract 2: Mantis streaming agent adapter (Layer 4).

Bridges the Mantis reasoning agent to Server-Sent Events. Every phase, tool call,
hypothesis, and token emitted here originates from a real agent run against a real
model. When the agent cannot run, this adapter emits an explicit `error` event
rather than inventing findings.

A note on what this module deliberately does NOT do. An earlier version answered
triage requests by calling `mantis.tools.diagnostics.diagnose_test_failure` and
streaming its output as the reply. That tool is deterministic and evaluates a fixed
catalogue of nine known failure modes, emitting a verdict for every one of them on
every run. The result looked like an analysis but was a checklist: it returned in
milliseconds, always produced the same nine rows regardless of the failure, and its
templated prose frequently rendered unfilled placeholders. Triage now runs the real
agent, and that tool is offered to it as one evidence source among many.
"""

from datetime import datetime, timezone
import json
import os
import queue
import re
import threading
import time
from typing import Any, Dict, Generator, List, Optional, Tuple

from mantis.agent import split_audit_section
from mantis.config import CONFIG, ProviderType
from mantis.models import ChatMessage, MessageRole, SessionContext
from mantis.session import SessionManager
from mantis.tools.registry import get_mcp_tools
from workbench.server.logger import SERVER_LOGGER
from workbench.server.notifications import NotificationError, Notifier

# The report a Workbench triage produces is read by lab operators and by external
# device manufacturers, who may implement UDMI from its documentation without any UDMI
# library and have never seen this repository. It therefore leads with the observable
# contract (what the device sent against what UDMI expects) rather than sequencer
# internals, and it closes with the hypothesis audit the gate enforces, placed in a
# trailing section that the adapter moves out of the answer into the collapsed matrix.
DEVICE_REPORT_INSTRUCTIONS = [
    "Write a DEVICE-FACING report for lab operators and for the device's manufacturer, "
    "who may implement UDMI from its documentation without UDMI libraries and has never "
    "seen this repository. The device under test is external: it is not necessarily "
    "pubber, and pubber source is never evidence of how it behaves. Use these sections, "
    "in this order:",
    "## What the device did vs what UDMI expects",
    "- Config the sequencer sent: the config field paths and values that matter to "
    "this check, with the timestamp of each config message from the run artifacts.",
    "- What the device published: the state and event messages it sent (field paths, "
    "values, timestamps) from the run artifacts, or an explicit statement that it "
    "published nothing relevant in the window.",
    "- The check that failed: quote the sequencer's failure message exactly as it "
    "appears in sequence.md or the RESULT line.",
    "- What UDMI expects: the expected field path, value, and timing for that check.",
    "- Governing specification: the doc under docs/ and the schema under schema/*.json "
    "that define this behavior, cited by repository-relative path.",
    "## What to tell the manufacturer / what to fix on the device",
    "Plain language, no sequencer internals: what the device must change so that it "
    "sends what UDMI expects. If the evidence shows the fault is NOT in the device "
    "(test harness, backend, or site model), say so plainly in this section, name the "
    "component that must change instead, and state that the device needs no change.",
    "## Hypothesis Resolution Audit",
    "Put the audit LAST, under exactly this heading, in the form the scoping directive "
    "specifies. Do not discuss hypotheses or verdicts anywhere above it.",
]

TRIAGE_KEYWORDS = ("fail", "triage", "root cause", "why did", "diagnose", "debug")
AGENT_TIMEOUT_SECONDS = 600


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


class MantisSessionStore:
    """Tracks conversational sessions and streams agent activity as SSE events."""

    def __init__(self, session_mgr: SessionManager, notifier: Optional[Notifier] = None):
        self.session_mgr = session_mgr
        # Delivers "Email me when done" results. None means the host offers no
        # notifications, and a request that asks for one is refused.
        self.notifier = notifier
        self._sessions: Dict[str, SessionContext] = {}
        # One entry per session while an agent run is alive: the run's cancel event
        # and its worker thread. The worker, not the SSE stream, defines "alive": a
        # stream can end (stop, disconnect, timeout) while the worker is still
        # finishing a model call it cannot abandon mid-request.
        self._runs: Dict[str, Tuple[threading.Event, threading.Thread]] = {}
        self._runs_lock = threading.Lock()

    # ------------------------------------------------------------ sessions ---
    def get_or_create(self, session_id: str, overrides: Optional[Dict[str, Any]] = None) -> SessionContext:
        context = self._sessions.setdefault(session_id, SessionContext(active_session_id=session_id))
        if overrides:
            if overrides.get("site_model"):
                context.active_site_model = overrides["site_model"]
            if overrides.get("device_id"):
                context.active_device_id = overrides["device_id"]
            if overrides.get("test_id"):
                context.active_test_id = overrides["test_id"]
        return context

    def _live_run(self, session_id: str) -> Optional[Tuple[threading.Event, threading.Thread]]:
        with self._runs_lock:
            run = self._runs.get(session_id)
            if run and not run[1].is_alive():
                del self._runs[session_id]
                return None
            return run

    def stop_session(self, session_id: str) -> Dict[str, Any]:
        """Asks the session's running agent to stop at its next step boundary."""
        run = self._live_run(session_id)
        if run is None:
            return {
                "session_id": session_id,
                "status": "NOT_RUNNING",
                "message": "No Mantis run is active for this session; nothing was stopped.",
            }
        run[0].set()
        return {
            "session_id": session_id,
            "status": "STOPPING",
            "message": (
                "Mantis will stop at its next step boundary. A model call or tool "
                "already in progress finishes first; no answer from this run is kept."
            ),
        }

    def clear_session(self, session_id: str) -> Dict[str, Any]:
        self.stop_session(session_id)
        self.get_or_create(session_id).history.clear()
        return {"session_id": session_id, "status": "CLEARED"}

    def get_status(self, session_id: str) -> Dict[str, Any]:
        context = self.get_or_create(session_id)
        return {
            "session_id": session_id,
            "site_model": context.active_site_model,
            "device_id": context.active_device_id,
            "test_id": context.active_test_id,
            "turns": len(context.history),
            "tools_available": len(get_mcp_tools()),
        }

    # -------------------------------------------------------------- stream ---
    def stream_chat_events(
        self, payload: Dict[str, Any], correlation_id: str
    ) -> Generator[Dict[str, Any], None, None]:
        """Yields Contract 2 SSE events for one chat or triage request."""
        session_id = payload.get("session_id") or "sess-default"
        message = (payload.get("message") or "").strip()
        if not message:
            yield {"event": "error", "data": {"message": "Field 'message' must not be empty."}}
            return

        # A second run on the same session would interleave with the first in the
        # shared history, so it is refused until the previous worker has exited.
        if self._live_run(session_id) is not None:
            yield {
                "event": "error",
                "data": {
                    "message": (
                        "The previous Mantis run for this session is still active (a stopped "
                        "run finishes its current model call before exiting). Wait a few "
                        "seconds and send again."
                    )
                },
            }
            return

        notify = payload.get("notify", False)
        refusal = self._notify_refusal(notify, message)
        if refusal:
            yield {"event": "error", "data": {"message": refusal}}
            return

        context = self.get_or_create(session_id, payload.get("context") or {})

        # Extract test_id from message if not explicitly supplied in context
        if not context.active_test_id:
            quoted = re.search(r"['\"]([a-zA-Z0-9_+]+(?:_[a-zA-Z0-9_+]+)+)['\"]", message)
            if quoted:
                context.active_test_id = quoted.group(1)
            else:
                test_match = re.search(
                    r"\b(?:test|sequence)\s+['\"]?([a-zA-Z0-9_+]+)['\"]?",
                    message,
                    re.IGNORECASE,
                )
                if test_match:
                    context.active_test_id = test_match.group(1)
                else:
                    for word in re.findall(r"\b[a-z]+(?:_[a-z0-9]+)+\b", message):
                        if not any(
                            word.startswith(p)
                            for p in ("how_", "why_", "what_", "when_", "where_", "can_")
                        ):
                            context.active_test_id = word
                            break

        # Extract device_id from message if not in context
        if not context.active_device_id:
            dev_match = re.search(
                r"\b(?:device|dut)\s+['\"]?([A-Za-z0-9_-]+)['\"]?", message, re.IGNORECASE
            )
            if dev_match:
                context.active_device_id = dev_match.group(1)

        # Default site model if not set
        if not context.active_site_model:
            site_match = re.search(r"(sites/[a-zA-Z0-9_\-\./]+)", message)
            if site_match:
                context.active_site_model = site_match.group(1)
            else:
                context.active_site_model = "sites/udmi_site_model"

        context.history.append(
            ChatMessage(role=MessageRole.USER, content=message, timestamp=_now())
        )

        SERVER_LOGGER.info(
            "MantisStreamAdapter",
            "sse.connect",
            correlation_id=correlation_id,
            layer="MANTIS_SSE",
            context={
                "sessionId": session_id,
                "deviceId": context.active_device_id,
                "testId": context.active_test_id,
            },
        )

        if message.startswith("/"):
            yield from self._slash_command(session_id, message, context)
            return

        # Mantis is a reasoning agent. Without a model provider there is no analysis
        # to give, and emitting a canned report in its place is what made the previous
        # implementation untrustworthy: it looked like a diagnosis and was not one.
        if CONFIG.provider == ProviderType.OFFLINE_DETERMINISTIC:
            yield {
                "event": "error",
                "data": {
                    "message": (
                        "Mantis has no model provider configured, so it cannot analyse this "
                        "failure. MANTIS_OFFLINE is set, which disables the reasoning engine. "
                        "Unset MANTIS_OFFLINE and provide either GEMINI_API_KEY (AI Studio) or "
                        "Google Cloud application-default credentials (Vertex AI), then retry."
                    )
                },
            }
            return

        started = time.time()
        if self._is_triage(message, context):
            from workbench.server.discovery import DiscoveryError

            try:
                prompt = self._triage_prompt(context)
            except DiscoveryError as exc:
                yield {
                    "event": "error",
                    "data": {
                        "message": (
                            f"Cannot locate recorded artifacts for triage: site model "
                            f"'{context.active_site_model}' did not resolve: {exc}"
                        )
                    },
                }
                return
            if prompt is None:
                yield {
                    "event": "error",
                    "data": {
                        "message": (
                            "Triage requires both a device and a test. "
                            "Select a failed test first, or ask a general question instead."
                        )
                    },
                }
                return
        else:
            prompt = message

        run_metrics: Dict[str, Any] = {}
        yield from self._run_agent(
            prompt=prompt,
            context=context,
            session_id=session_id,
            correlation_id=correlation_id,
            metrics_sink=run_metrics,
            notify_question=message if notify else None,
        )

        run_metrics["total_duration_sec"] = round(time.time() - started, 2)
        yield {
            "event": "done",
            "data": {"session_id": session_id, "metrics": run_metrics},
        }

    def _notify_refusal(self, notify: Any, message: str) -> Optional[str]:
        """Returns why a notification request cannot be honoured, or None if it can.

        Checked before the run starts: accepting the request and discovering at the
        end that nothing can be delivered would lose the result the operator was
        relying on the email for.
        """
        if not isinstance(notify, bool):
            return "Field 'notify' must be true or false."
        if not notify:
            return None
        if message.startswith("/"):
            return (
                "Slash commands answer immediately, so 'Email me when done' does not apply "
                "to them. Turn it off and send the command again."
            )
        if self.notifier is None:
            return "This Workbench server was started without email notifications."
        try:
            self.notifier.require_ready()
        except NotificationError as exc:
            return f"Cannot notify you when this finishes: {exc}"
        return None

    def _udmi_root(self) -> str:
        return getattr(self.session_mgr, "udmi_root", None) or os.path.abspath(
            os.path.join(os.path.dirname(__file__), "..", "..")
        )

    @staticmethod
    def _is_triage(message: str, context: SessionContext) -> bool:
        lowered = message.lower().strip()
        if any(lowered.startswith(p) for p in ("/diagnose", "/triage", "diagnose", "triage", "run triage")):
            return True
        if re.search(r"\b(?:why did|how did)\b.*?\bfail\b", lowered) and re.search(r"\b(?:test|sequence)\b", lowered):
            return True
        return False

    def _triage_prompt(self, context: SessionContext) -> Optional[str]:
        """Builds the triage instruction, or None when there is nothing to triage.

        The deterministic `diagnose_test_failure` tool is named here as the agent's
        FIRST step rather than used as the answer. That tool harvests a timeline and
        checks a fixed catalogue of known failure modes, which is genuinely useful
        evidence; what it cannot do is decide which of its findings explains THIS
        failure, because it emits a verdict for every hypothesis it knows about
        whether or not that hypothesis is relevant. Handing its output to the agent
        as evidence, and requiring the agent to verify it against the actual logs,
        is the difference between a checklist and a diagnosis.
        """
        device_id = context.active_device_id
        test_id = context.active_test_id
        if not device_id or not test_id:
            return None

        site_model = context.active_site_model
        # Resolution errors (unknown or malformed site model) propagate to the caller,
        # which reports them as an explicit error instead of triaging without logs.
        run_dir = self._run_dir(context)

        lines = [
            f"Diagnose why sequencer test '{test_id}' failed for device '{device_id}' "
            f"in site model '{site_model}'.",
            "",
            "Investigation requirements:",
        ]
        if run_dir:
            lines += [
                f"1. Call diagnose_test_failure(test_id='{test_id}', device_id='{device_id}', "
                f"site_model='{site_model}', run_dir='{run_dir}') first to harvest the "
                "deterministic timeline, transaction ids, and cutoff thresholds from the "
                "recorded run. Pass exactly this run_dir to every timeline or log tool you "
                "call for this run.",
                "2. Treat that tool's output as EVIDENCE, not as a conclusion. It reports a "
                "verdict for every failure mode it knows about, including ones irrelevant to "
                "this run. Verify anything you intend to rely on against the actual sequence "
                "logs and the UDMI source before citing it.",
                "3. Establish which single mechanism accounts for the failure. If the evidence "
                "cannot settle it, say so and state what would.",
                f"4. The recorded artifacts for this run are on disk at: {run_dir}",
            ]
        else:
            lines += [
                f"1. There is no recorded run for this test: no artifact directory exists at "
                f"'{site_model}/out/devices/{device_id}/tests/{test_id}'. Do NOT call "
                "diagnose_test_failure, get_test_timeline, or any other log or timeline tool; "
                "without a run directory they would read unrelated logs.",
                "2. Reason only from the UDMI source, schemas, and the site model, and say "
                "explicitly in your answer that no runtime log evidence exists rather than "
                "presenting source inference as observation.",
                "3. State which recorded artifacts would settle the failure.",
            ]

        lines += [""] + DEVICE_REPORT_INSTRUCTIONS
        return "\n".join(lines)

    def _run_dir(self, context: SessionContext) -> Optional[str]:
        """Locates the recorded artifact directory for the active test.

        Returns None only when the site model resolves but holds no recorded run for
        this device and test. A site model that cannot be resolved raises
        DiscoveryError: that is a configuration fault, not an absence of evidence.
        """
        if not (context.active_site_model and context.active_device_id and context.active_test_id):
            return None
        udmi_root = self._udmi_root()
        from workbench.server.discovery import resolve_site_model

        site_dir = resolve_site_model(udmi_root, context.active_site_model)
        test_dir = os.path.abspath(os.path.join(
            site_dir, "out", "devices", context.active_device_id,
            "tests", context.active_test_id,
        ))
        return test_dir if os.path.isdir(test_dir) else None

    def _run_agent(
        self,
        prompt: str,
        context: SessionContext,
        session_id: str,
        correlation_id: str,
        metrics_sink: Dict[str, Any],
        notify_question: Optional[str] = None,
    ) -> Generator[Dict[str, Any], None, None]:
        """Streams one real MantisAgent run as Contract 2 SSE events.

        Two channels arrive from the agent and are deliberately kept apart. The prose
        channel carries interim reasoning and orchestrator narration; it is surfaced as
        `thought`, which the UI renders in a collapsible disclosure. Only the answer the
        agent finally returns is surfaced as `token`. Mixing the two is what previously
        produced an unreadable reply: ReAct step narration and critic audit text landed
        in the same bubble as the conclusion.

        With `notify_question` set, the run is detached from the stream: closing the
        tab no longer cancels it, and the worker emails the answer (or the error that
        ended the run) when it finishes. An operator Stop still cancels it, and a
        stopped run sends nothing.
        """
        from mantis.agent import MantisAgent, MantisCancelled

        events: "queue.Queue[Dict[str, Any]]" = queue.Queue()
        SENTINEL = "__final__"
        audit_rows: List[Dict[str, Any]] = []
        # Captured now: a later turn may change the session's active device or test
        # before a detached run finishes.
        run_context = {
            "site_model": context.active_site_model,
            "device_id": context.active_device_id,
            "test_id": context.active_test_id,
        }
        started = time.time()

        def on_token(chunk: str) -> None:
            events.put({"kind": "prose", "text": chunk})

        def on_event(record: Dict[str, Any]) -> None:
            if record.get("type") == "audit":
                audit_rows[:] = record.get("hypotheses") or []
            events.put({"kind": "record", "record": record})

        cancel_event = threading.Event()

        def worker() -> None:
            answer, error = "", None
            try:
                agent = MantisAgent()
                answer = agent.run(
                    prompt,
                    context=context,
                    stream_callback=on_token,
                    event_callback=on_event,
                    cancel_event=cancel_event,
                ) or ""
                events.put({"kind": SENTINEL, "answer": answer, "error": None})
            except MantisCancelled as exc:
                # Recorded so the next turn knows this question went unanswered,
                # rather than reading the unanswered question as the latest context.
                context.history.append(
                    ChatMessage(
                        role=MessageRole.ASSISTANT,
                        content=f"[Run stopped by operator: {exc}]",
                        timestamp=_now(),
                    )
                )
                events.put({"kind": SENTINEL, "answer": "", "error": None, "cancelled": str(exc)})
                return  # a stopped run is never emailed
            except Exception as exc:
                error = f"{exc.__class__.__name__}: {exc}"
                events.put({"kind": SENTINEL, "answer": "", "error": error})
            if notify_question is not None:
                self._notify_mantis(
                    notify_question, run_context, answer, error, audit_rows,
                    time.time() - started, correlation_id,
                )

        thread = threading.Thread(target=worker, daemon=True)
        with self._runs_lock:
            self._runs[session_id] = (cancel_event, thread)
        thread.start()
        try:
            yield from self._drain_agent_events(
                events, SENTINEL, cancel_event, context, session_id, correlation_id,
                metrics_sink, detached=notify_question is not None,
            )
        finally:
            # However the stream ends -- answer, stop, timeout, or the client closing
            # the connection (GeneratorExit) -- the worker must not keep calling the
            # model and running tools for a reader that is gone. The one exception is
            # a run the operator asked to be notified about: its reader is the inbox.
            if notify_question is None:
                cancel_event.set()

    def _notify_mantis(
        self,
        question: str,
        run_context: Dict[str, Optional[str]],
        answer: str,
        error: Optional[str],
        audit_rows: List[Dict[str, Any]],
        duration_sec: float,
        correlation_id: str,
    ) -> None:
        """Queues the email for a finished run, split into report and audit as the panel shows it."""
        from workbench.server.notify_compose import compose_mantis

        if not error and not answer.strip():
            error = "Mantis completed without producing an answer."
        body, matrix = answer, None
        if not error and audit_rows:
            report, audit_section = split_audit_section(answer)
            body = report if report.strip() else (
                "_Mantis returned only its Hypothesis Resolution Audit, with no report "
                "above it. Re-run the triage._"
            )
            matrix = {
                "hypotheses": [
                    {
                        "hypothesis": row.get("hypothesis"),
                        "verdict": row.get("verdict") or "UNRESOLVED",
                        "rationale": row.get("rationale") or None,
                        "evidence_tier": row.get("evidence_tier") or None,
                    }
                    for row in audit_rows
                ],
                "audit_markdown": audit_section,
            }
        udmi_root = self._udmi_root()
        self.notifier.send_in_background(
            "mantis",
            lambda: compose_mantis(udmi_root, question, run_context, body, matrix, error, duration_sec),
            correlation_id=correlation_id,
        )

    def _drain_agent_events(
        self,
        events: "queue.Queue[Dict[str, Any]]",
        SENTINEL: str,
        cancel_event: threading.Event,
        context: SessionContext,
        session_id: str,
        correlation_id: str,
        metrics_sink: Dict[str, Any],
        detached: bool = False,
    ) -> Generator[Dict[str, Any], None, None]:
        """Relays one agent run's queued output as Contract 2 SSE events."""

        pending_hypotheses: List[str] = []
        # The final audit matrix is held back until the answer arrives: it is shown
        # below the report, and it carries the audit section split off that answer.
        held_audit: List[Dict[str, Any]] = []
        deadline = time.time() + AGENT_TIMEOUT_SECONDS

        while time.time() < deadline:
            try:
                item = events.get(timeout=0.5)
            except queue.Empty:
                continue

            kind = item["kind"]

            if kind == "prose":
                yield {"event": "thought", "data": {"text": item["text"]}}
                continue

            if kind == "record":
                translated_events = self._translate(item["record"], pending_hypotheses, metrics_sink)
                if item["record"].get("type") == "audit":
                    held_audit[:] = translated_events
                    continue
                for translated in translated_events:
                    yield translated
                continue

            # Sentinel: the run has finished one way or the other.
            if item.get("cancelled"):
                yield {"event": "error", "data": {"message": item["cancelled"]}}
                return
            if item["error"]:
                SERVER_LOGGER.error(
                    "MantisStreamAdapter",
                    "agent.error",
                    correlation_id=correlation_id,
                    layer="MANTIS_SSE",
                    context={
                        "sessionId": session_id,
                        "deviceId": context.active_device_id,
                        "testId": context.active_test_id,
                    },
                    error={"code": "AGENT", "message": item["error"]},
                )
                yield {"event": "error", "data": {"message": item["error"]}}
                return

            answer = item["answer"]
            if not answer.strip():
                yield {
                    "event": "error",
                    "data": {"message": "Mantis completed without producing an answer."},
                }
                return

            # MantisAgent.run has already recorded the full answer, audit included, in
            # the session history, so a follow-up turn is conditioned on what was ruled
            # out. Appending it here as well stored every answer twice.
            if not held_audit:
                yield {"event": "token", "data": {"text": answer}}
                return

            report, audit_section = split_audit_section(answer)
            for matrix_event in held_audit:
                matrix_event["data"]["audit_markdown"] = audit_section
            if not report.strip():
                yield {
                    "event": "error",
                    "data": {
                        "message": (
                            "Mantis returned only its Hypothesis Resolution Audit, with no "
                            "report above it. The audit is shown below; re-run the triage."
                        )
                    },
                }
            else:
                yield {"event": "token", "data": {"text": report}}
            yield from held_audit
            return

        if detached:
            message = (
                f"Mantis is still working after {AGENT_TIMEOUT_SECONDS}s. This view has stopped "
                "following it; the run continues and its result will be emailed to you."
            )
        else:
            message = (
                f"Mantis exceeded the {AGENT_TIMEOUT_SECONDS}s analysis budget and was "
                "stopped. Narrow the question, or inspect the partial reasoning above."
            )
        yield {"event": "error", "data": {"message": message}}

    def _translate(
        self,
        record: Dict[str, Any],
        pending_hypotheses: List[str],
        metrics_sink: Dict[str, Any],
    ) -> List[Dict[str, Any]]:
        """Maps one agent record onto zero or more Contract 2 SSE events."""
        record_type = record.get("type")

        if record_type == "phase":
            return [{"event": "phase", "data": {"phase": record.get("phase")}}]

        if record_type == "tool_call":
            return [{
                "event": "tool_call",
                "data": {
                    "call_id": record.get("call_id"),
                    "tool": record.get("tool"),
                    "args": record.get("args") or {},
                    "timestamp": _now(),
                },
            }]

        if record_type == "tool_result":
            return [{
                "event": "tool_result",
                "data": {
                    "call_id": record.get("call_id"),
                    "tool": record.get("tool"),
                    "summary": record.get("status") or "completed",
                    "output": record.get("output"),
                },
            }]

        if record_type == "hypotheses":
            # The plan's hypotheses, shown as soon as the agent commits to them so the
            # operator can see what is being investigated while it is still running.
            pending_hypotheses[:] = record.get("hypotheses") or []
            if not pending_hypotheses:
                return []
            return [{
                "event": "hypothesis_matrix",
                "data": {
                    "hypotheses": [
                        {
                            "hypothesis": text,
                            "verdict": "UNRESOLVED",
                            "rationale": "Under investigation.",
                            "evidence_tier": "NONE",
                        }
                        for text in pending_hypotheses
                    ],
                    "final": False,
                },
            }]

        if record_type == "audit":
            rows = record.get("hypotheses") or []
            if not rows:
                return []
            # Rationale and evidence tier are forwarded only when the agent supplied
            # them. Deriving an evidence tier from the presence of a verdict claimed
            # LOCAL_FILE evidence for conclusions that may rest on source alone.
            return [{
                "event": "hypothesis_matrix",
                "data": {
                    "hypotheses": [
                        {
                            "hypothesis": row.get("hypothesis"),
                            "verdict": row.get("verdict") or "UNRESOLVED",
                            "rationale": row.get("rationale") or None,
                            "evidence_tier": row.get("evidence_tier") or None,
                        }
                        for row in rows
                    ],
                    "final": True,
                },
            }]

        if record_type == "metrics":
            metrics_sink.update({
                "steps": record.get("steps"),
                "tool_calls": record.get("tool_calls"),
                "tripartite_status": record.get("tripartite_status"),
                "agent_duration_sec": record.get("duration_sec"),
            })
            return []

        return []


    def _slash_command(
        self, session_id: str, command: str, context: SessionContext
    ) -> Generator[Dict[str, Any], None, None]:
        """Handles deterministic in-chat slash commands."""
        parts = command.split()
        verb = parts[0].lower()

        if verb == "/clear":
            self.clear_session(session_id)
            reply = "Conversation history cleared. Workspace context preserved."
        elif verb == "/status":
            reply = f"```json\n{json.dumps(self.get_status(session_id), indent=2)}\n```"
        elif verb == "/logs":
            window = parts[1] if len(parts) > 1 else "main"
            try:
                reply = f"```\n{self.session_mgr.get_test_logs(test_id=session_id, window=window, lines=80)}\n```"
            except Exception as exc:
                reply = f"Unable to read window '{window}': {exc}"
        elif verb == "/help":
            reply = (
                "**Commands**\n"
                "- `/status` — active session context and tool count\n"
                "- `/logs <window>` — recent output from a tmux window\n"
                "- `/clear` — clear conversation history\n"
                "- `/help` — this message"
            )
        else:
            reply = f"Unknown command `{verb}`. Try `/help`."

        yield {"event": "token", "data": {"text": reply}}
        yield {"event": "done", "data": {"session_id": session_id, "metrics": {}}}
