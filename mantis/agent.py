"""Mantis ReAct Cognitive Planner and Built-in Adversarial Critique Engine."""

import json
import logging
import os
import re
import threading
from typing import Any, Callable, Dict, List, Optional, Tuple

logger = logging.getLogger(__name__)

from mantis.config import CONFIG, ModelTier, ProviderType
from mantis.skills import SkillManager
from mantis.tools.artifacts import (
    discover_test_runs,
    extract_log_slice,
    extract_timeline,
    ingest_support_bundle,
)
from mantis.tools.differential import compare_test_runs
from mantis.tools.patcher import patch_site_model
from mantis.tools.schemas import inspect_udmi_schema, list_udmi_schemas
from mantis.tools.site_models import inspect_site_model
from mantis.session import SessionManager


from mantis.context import ContextManager
from mantis.models import SessionContext
from mantis.tools.diagnostics import diagnose_test_failure as deterministic_diagnose


class MantisCancelled(Exception):
    """Raised when the caller's cancel event is set during a run.

    Cancellation is cooperative: it is observed at step boundaries (before each model
    call, before each tool execution, and before the Critic and Arbitrator phases). A
    model call or tool already in flight finishes first, because neither the SDK
    request nor a running tool can be interrupted safely mid-way.
    """


# Number of remaining ReAct steps at which the orchestrator begins warning the model
# to converge, so it is never cut off mid-investigation without producing an answer.
BUDGET_WARNING_THRESHOLD = 3

# The only verdicts a hypothesis audit may use. There is deliberately no blanket
# "CONFIRMED": it permitted every competing hypothesis to be endorsed at once,
# which excludes nothing and is as unfalsifiable as dropping them. Ranking forces
# the Actor to say which explanation actually accounts for the failure.
HYPOTHESIS_VERDICTS = ("PRIMARY", "CONTRIBUTING", "REFUTED", "UNRESOLVED")

# Subsystems whose behavior is a server-side runtime phenomenon. Their source shows
# intent; only their logs show what happened. A verdict on one of these is allowed
# without logs, but it must say so rather than presenting an inference as a finding.
BACKEND_SUBSYSTEMS = ("udmis",)

# Tiers reported by the get_udmis_runtime_logs tool, mirrored here as the vocabulary
# an audit must use. NONE must additionally carry its qualifier so the reader can
# tell a log-backed conclusion from a plausible reading of the source.
RUNTIME_EVIDENCE_TIERS = ("LOCAL_FILE", "CLOUD", "NONE")
RUNTIME_EVIDENCE_NONE_QUALIFIER = "source inference only"

# The scopes a scoping plan may declare. INFORMATIONAL_SCOPE marks a question about
# how something works rather than a defect report: there is no failure to explain,
# so competing hypotheses, the resolution gate, and runtime-evidence declarations
# do not apply. Forcing them onto an explanation produced a ceremony of "H1 PRIMARY,
# H2 REFUTED" around what should have been a direct, complete answer.
FAILURE_SCOPES = ("SINGLE_TARGET", "TOTAL_OUTAGE", "MULTI_TARGET_DEGRADATION", "NOT_A_FAILURE")
INFORMATIONAL_SCOPE = "NOT_A_FAILURE"


# Clause-joining phrases that show a hypothesis asserts more than one mechanism.
# A compound hypothesis cannot be ranked as a unit: whichever half is best evidenced
# carries the other into the verdict with it. Deliberately excludes a bare "and",
# which routinely joins parts of a single mechanism ("routing and dispatch layer")
# and would reject sound hypotheses.
COMPOUND_HYPOTHESIS_MARKERS = (
    " while ",
    " whilst ",
    " as well as ",
    " in addition to ",
    " combined with ",
    " along with ",
    " simultaneously ",
    " and also ",
    " plus ",
    "; ",
)


def _compound_markers(text: str) -> List[str]:
    """Returns the clause-joining markers found in a hypothesis, if any."""
    padded = f" {text.lower()} "
    return [marker.strip() for marker in COMPOUND_HYPOTHESIS_MARKERS if marker in padded]


# Vertex reserves `$ref` inside `function_response.response`: any object carrying
# that key is read as a pointer to a file part attached to the request.
_RESERVED_REF_KEY = "$ref"
#: What `$ref` is renamed to. Plain `ref` reads identically to a model looking at
#: a JSON Schema excerpt, and probing confirms it is not treated as a reference.
_SAFE_REF_KEY = "ref"


def _neutralize_schema_refs(payload):
    """Rename the reserved `$ref` key so Vertex stops reading tool output as refs.

    Vertex scans `function_response.response` for `$ref` and resolves each one
    against the file parts attached to the request. UDMI tool output is full of
    real JSON Schema, and every `$ref` in it points at another schema file rather
    than at an attached part, so the API rejects the whole request with
    400 INVALID_ARGUMENT and the investigation dies mid-run.

    The trigger is the KEY, not the value. That was established by probing the
    live API: with a `$ref` key present, every value was rejected identically,
    including an inert control string of ordinary prose; with the key renamed,
    even `file:common.json#/definitions/depth` passes untouched. An earlier fix
    here stripped the `file:` scheme out of the values instead, which did not
    address the trigger at all and silently corrupted schema content on the way
    through. Renaming the key is both sufficient and lossless.

    Applies at every depth, including inside arrays, because `allOf`/`oneOf`
    wrap their `$ref`s in lists.
    """
    if isinstance(payload, dict):
        return {
            (_SAFE_REF_KEY if key == _RESERVED_REF_KEY else key):
                _neutralize_schema_refs(value)
            for key, value in payload.items()
        }
    if isinstance(payload, list):
        return [_neutralize_schema_refs(item) for item in payload]
    return payload



def _part_carries_content(part) -> bool:
    """Whether a part carries anything the API will accept as content.

    An empty text part is not content: a turn made only of those is rejected with
    400 'must include at least one parts field'. Function calls, function responses
    and thought signatures all count, the last because Vertex requires signatures to
    survive the round-trip.
    """
    if (getattr(part, "text", None) or "").strip():
        return True
    return bool(
        getattr(part, "function_call", None)
        or getattr(part, "function_response", None)
        or getattr(part, "thought_signature", None)
    )


def _coalesce_model_parts(parts):
    """Rebuild a valid model turn from parts accumulated across stream chunks.

    Streaming splits a single logical model response into many fragments: dozens of
    partial text parts, plus (when thinking is enabled) a trailing part that carries
    a thought_signature and no content at all. Replaying that raw list as conversation
    history sends a model turn containing content-less parts, which the API rejects.

    This merges consecutive text fragments into one part, passes function_call parts
    through untouched, and folds any signature-only part's thought_signature onto the
    most recent emitted part so the signature survives the round-trip. Vertex requires
    thought_signature preservation, so signatures are never discarded: if one arrives
    before any content exists to carry it, that part is kept verbatim.
    """
    from google.genai import types  # type: ignore  # lazy: SDK is optional offline

    coalesced = []
    pending_text = []

    def flush_text():
        if not pending_text:
            return
        merged = "".join(pending_text)
        pending_text.clear()
        coalesced.append(types.Part.from_text(text=merged))

    for part in parts:
        text = getattr(part, "text", None)
        function_call = getattr(part, "function_call", None)
        signature = getattr(part, "thought_signature", None)

        if function_call:
            flush_text()
            coalesced.append(part)
            continue

        if text:
            pending_text.append(text)
            continue

        if signature:
            # Content-less part carrying only a thought signature.
            flush_text()
            if coalesced:
                coalesced[-1].thought_signature = signature
            else:
                coalesced.append(part)
            continue

        # No text, no function call, no signature: carries nothing, so drop it.

    flush_text()
    return coalesced

# Issued to the Actor on its first step, which runs with tool access withheld so that
# scoping cannot be skipped. The resulting plan lands in the Actor's own model turn,
# making it a self-authored commitment rather than an externally imposed instruction.
SCOPING_DIRECTIVE = """[Orchestrator Directive — Scoping Step]
Tool access is withheld for this turn. Before investigating, establish the blast radius
of the reported problem and commit to a plan. Emit EXACTLY this structure and nothing else:

FAILURE_SCOPE: <SINGLE_TARGET | TOTAL_OUTAGE | MULTI_TARGET_DEGRADATION | NOT_A_FAILURE>
SCOPE_JUSTIFICATION: <one sentence citing the specific evidence of breadth in the report>
COMPETING_HYPOTHESES:
- H1: <hypothesis>
- H2: <hypothesis>
- H3: <hypothesis>
REQUIRED_SUBSYSTEMS: <comma-separated repository directories you will examine>

If FAILURE_SCOPE is NOT_A_FAILURE, OMIT the COMPETING_HYPOTHESES block entirely.
A question about how something works has no failure to explain, so there is nothing
to hypothesize about or rank. Emit only FAILURE_SCOPE, SCOPE_JUSTIFICATION, and
REQUIRED_SUBSYSTEMS. Every rule below about hypotheses, the Hypothesis Resolution
Audit, and RUNTIME_EVIDENCE applies ONLY to defect reports.

Rules for COMPETING_HYPOTHESES:
- Each hypothesis must assert EXACTLY ONE mechanism in ONE subsystem. A hypothesis
  that joins two claims ("X is misconfigured while Y leaks threads") cannot be ranked,
  because whichever half is better evidenced drags the other into the verdict with it.
- If you believe two mechanisms are both at work, that is two hypotheses. Give them
  separate labels and let the audit rank them independently.

Scope definitions:
- SINGLE_TARGET: exactly one device/endpoint affected while peers are healthy.
- TOTAL_OUTAGE: every operation fails with hard refusal (connection or auth).
- MULTI_TARGET_DEGRADATION: several independent targets show timeouts, intermittent
  errors, or slow responses. This implicates shared infrastructure, not the targets.
- NOT_A_FAILURE: the request is informational rather than a defect report.

Rules for REQUIRED_SUBSYSTEMS:
- Name every repository subsystem on BOTH sides of any communication boundary
  implicated by the report. A client that sends a request and a service that
  processes it are two distinct subsystems.
- The device under test is NOT in this repository. It is usually third-party hardware
  or firmware built from the UDMI documentation, possibly without any UDMI library, and
  it is not necessarily pubber. Its side of a boundary is evidenced only by the messages
  it published during the run (state, events, and their timestamps in the run
  artifacts), judged against the UDMI specification under docs/ and the schemas under
  schema/. Never cite pubber source as evidence of how the device under test behaves.
- Omit a subsystem ONLY if it is architecturally incapable of producing the symptom.
  Do not omit one merely because you suspect another more strongly.
- If FAILURE_SCOPE is MULTI_TARGET_DEGRADATION you MUST include the shared backend
  subsystems that all affected targets depend on.
- Use real top-level directory names from this repository.

This plan is your own commitment. You will be held to it for the rest of the
investigation, and you must examine every subsystem you list here.

Your final answer will be rejected unless it includes a "Hypothesis Resolution Audit"
that assigns every hypothesis above exactly one verdict. Place it LAST, under exactly
the markdown heading `## Hypothesis Resolution Audit`, with nothing after it. The reader
sees the report above that heading; the audit below it is the record of what was ruled
out. Write one entry per hypothesis in this form:

- **H<n>: <the hypothesis>**
  - Verdict: <verdict>
  - RUNTIME_EVIDENCE: <tier> (backend hypotheses only)
  - Rationale: <the evidence for this verdict, citing artifacts, logs, docs, or source>

Verdicts:
- PRIMARY: this is the root cause. EXACTLY ONE hypothesis may be PRIMARY.
- CONTRIBUTING: real and evidenced, but a secondary effect rather than the cause.
- REFUTED: excluded. You must name the evidence that excludes it.
- UNRESOLVED: your evidence cannot decide. You must state what evidence would.

Marking everything as a cause is not a resolution. The verdict for each hypothesis
must rest on evidence that supports THAT hypothesis specifically: evidence showing a
different hypothesis is true does not confirm this one.

Backend behavior is a runtime phenomenon. Source code shows what the backend was
meant to do; only its logs show what it did. Call get_udmis_runtime_logs to find out
which tier of runtime evidence exists for this incident, and give every hypothesis
about a backend subsystem its own declaration line in the audit:

  RUNTIME_EVIDENCE: LOCAL_FILE | CLOUD | NONE (source inference only)

Use the tier that tool reports. NONE is fully acceptable and is expected for an
older incident: it means the logs that would prove the mechanism no longer exist and
you are arguing from the source, which may still be the correct conclusion. What is
not acceptable is stating an inference as though it were an observation."""

# Issued in place of the scoping acceptance notice when the plan declares
# INFORMATIONAL_SCOPE. The readers of these answers include lab operators and device
# manufacturers who implement UDMI from its documentation rather than its libraries,
# so an explanation is framed in terms of the messages a device exchanges, not the
# sequencer's Java internals. An earlier explanation of a scheduled-scan test read
# only the first lines of its helper and described two of its many checks: the
# completeness rule below exists because a partial reading looks like a full one.
# Tools whose output is the source text an explanation is built from. In informational
# mode the Critic receives their output uncut: a Critic shown the first 6000 characters
# of a 400-line read declared the helper beyond the cut "never read", and the
# Arbitrator then deleted the correct step-by-step checks taken from it.
SOURCE_EVIDENCE_TOOLS = frozenset(
    {"read_udmi_file", "inspect_sequencer_test", "inspect_udmi_schema", "locate_udmi_doc"}
)

INFORMATIONAL_DIRECTIVE = """[Orchestrator Notice] Scoping accepted: this is an informational
question, not a defect report. Tool access is now restored.

Do NOT produce competing hypotheses, a Hypothesis Resolution Audit, verdicts
(PRIMARY/CONTRIBUTING/REFUTED/UNRESOLVED), RUNTIME_EVIDENCE declarations, or an RCA
report. Answer the question directly.

Investigate until the answer is complete and verified:
- When the question is about a sequencer test, read the ENTIRE test method and every
  helper it calls, to the end of each helper. List every step the test performs and
  every check it makes (checkThat, waitUntil, untilTrue, assert*, checkState, and any
  skip or precondition) in execution order, with the timing constants and tolerances
  it uses. Those checks are the pass criteria; omitting one misstates the test.
- If the question names a variant or parameter of a test (for example a `+<suffix>`
  on the test name), find where the sequencer parses it and state its effect from the
  source. Do not infer it from the name. Stop once you have found the code that
  consumes the value (for example the facet lookup in the test class); do not trace
  framework internals such as SequenceRunner or SequenceBase beyond that point.
- A read_udmi_file result marked truncated stops before the end of the file. If a
  helper you are explaining continues past the cut, read the remaining lines before
  describing it.
- Describe expected device behavior as UDMI messages: the exact config field paths the
  test sets, the state field paths and values the device must report, and the events
  it must publish. Confirm every field path with inspect_udmi_schema or the source.
  Never name a field you have not seen in a schema or in code.
- Locate the specification for the feature under docs/ (locate_udmi_doc) and cite it,
  so a reader implementing UDMI without its libraries can follow the contract.
- Keep what the UDMI specification requires distinct from how the sequencer checks it.

Structure the answer as:
1. Summary: two or three sentences stating what the behavior is.
2. Step by step: what happens, in order, with the check made at each step.
3. What the device must do: config received, state reported, events published, with
   field paths and timing expectations.
4. References: the spec documents, schemas, and source files you used, by path.
Add a diagram only if it clarifies an interaction or sequence."""


# The audit is enforced on the whole answer by `_audit_violations`, but it is written
# as a trailing section so that consumers can show the device-facing report first and
# the audit as supporting record. These helpers are the one place that section is
# located and parsed; the Workbench adapter imports them rather than re-deriving them.
AUDIT_SECTION_TITLE = "Hypothesis Resolution Audit"
_AUDIT_HEADING_RE = re.compile(
    rf"^[ \t]*#{{1,6}}[ \t]+\**[ \t]*{AUDIT_SECTION_TITLE}\b[^\n]*$",
    re.IGNORECASE | re.MULTILINE,
)
# A hypothesis entry starts at a line whose first token (after list, quote, table,
# heading, or bold markers) is its label. Mentions of H<n> inside another entry's
# rationale are not at line start and so do not open a new entry.
_AUDIT_ENTRY_RE = re.compile(r"^[ \t>*+|#-]*\**[ \t]*H(\d+)\b", re.MULTILINE)
_LEADING_MARKERS_RE = re.compile(r"^[\s>*+|#-]+")
_VERDICT_PREFIX_RE = re.compile(
    r"^(?:verdict\s*\**\s*[:\-]\s*\**\s*)?(?:%s)\b\**\s*[:\-\u2013\u2014.]?\s*"
    % "|".join(HYPOTHESIS_VERDICTS),
    re.IGNORECASE,
)
_RUNTIME_LINE_RE = re.compile(
    r"^\**\s*RUNTIME_EVIDENCE\s*\**\s*:\s*\**\s*(%s)\b" % "|".join(RUNTIME_EVIDENCE_TIERS),
    re.IGNORECASE,
)
_RATIONALE_LABEL_RE = re.compile(
    r"^(?:rationale|evidence)\s*\**\s*[:\-]\s*\**\s*", re.IGNORECASE
)
_HEADER_LABEL_RE = re.compile(r"^\**\s*H\d+\b\**\s*[:.)\-\u2013\u2014]?\s*")


def split_audit_section(answer: str) -> Tuple[str, Optional[str]]:
    """Splits an answer into (report, audit section).

    The audit section runs from the LAST `Hypothesis Resolution Audit` markdown heading
    to the end of the answer, heading included. Returns (answer, None) when the answer
    carries no such heading.
    """
    matches = list(_AUDIT_HEADING_RE.finditer(answer or ""))
    if not matches:
        return answer, None
    start = matches[-1].start()
    return answer[:start].rstrip(), answer[start:].strip()


def parse_audit_entries(audit_section: str, hypothesis_count: int) -> Dict[int, Dict[str, Optional[str]]]:
    """Extracts the rationale and runtime-evidence tier written for each hypothesis.

    Each entry spans from its label line to the next entry's label line. Its rationale
    is the entry text with the label line (which restates the hypothesis), the verdict
    token, the RUNTIME_EVIDENCE line, and any `Rationale:`/`Evidence:` field label
    removed. When the entry is a single line, that line minus its label and verdict
    token is the rationale. Hypotheses with no entry map to None values.
    """
    result: Dict[int, Dict[str, Optional[str]]] = {
        index: {"rationale": None, "evidence_tier": None}
        for index in range(1, hypothesis_count + 1)
    }
    if not audit_section:
        return result
    starts: Dict[int, int] = {}
    for match in _AUDIT_ENTRY_RE.finditer(audit_section):
        index = int(match.group(1))
        if 1 <= index <= hypothesis_count and index not in starts:
            starts[index] = match.start()
    ordered = sorted(starts.items(), key=lambda item: item[1])
    for position, (index, start) in enumerate(ordered):
        end = ordered[position + 1][1] if position + 1 < len(ordered) else len(audit_section)
        lines = [line for line in audit_section[start:end].splitlines() if line.strip()]
        header, body = lines[0], lines[1:]
        if not body:
            header_text = _HEADER_LABEL_RE.sub("", _LEADING_MARKERS_RE.sub("", header))
            body = [header_text]
        fragments: List[str] = []
        tier: Optional[str] = None
        for line in body:
            text = _LEADING_MARKERS_RE.sub("", line).strip()
            runtime = _RUNTIME_LINE_RE.match(text)
            if runtime:
                tier = runtime.group(1).upper()
                continue
            text = _VERDICT_PREFIX_RE.sub("", text)
            text = _RATIONALE_LABEL_RE.sub("", text)
            text = text.replace("**", "").strip()
            if text:
                fragments.append(text)
        result[index] = {
            "rationale": " ".join(fragments) or None,
            "evidence_tier": tier,
        }
    return result


class MantisAgent:
    """Autonomous diagnostic agent and ReAct cognitive core for UDMI."""

    def __init__(self, udmi_root: Optional[str] = None, client: Optional[Any] = None):
        if udmi_root is not None:
            self.udmi_root = os.path.abspath(udmi_root)
        else:
            self.udmi_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

        self.config = CONFIG
        self.client = client
        self.skills = SkillManager(udmi_root=self.udmi_root)
        self.session_mgr = SessionManager(udmi_root=self.udmi_root)
        self.active_site_model = "sites/udmi_site_model"
        self.active_session_id: Optional[str] = None

    # --------------------------------------------------------------------------
    # 3-Phase Cognitive Diagnostic Engine
    # --------------------------------------------------------------------------

    def diagnose_test_failure(
        self,
        test_id: str,
        device_id: str,
        site_model: str = "sites/udmi_site_model",
        run_dir: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Executes the mandatory 3-phase diagnostic cycle with built-in adversarial self-audit."""
        return deterministic_diagnose(
            test_id=test_id,
            device_id=device_id,
            site_model=site_model,
            run_dir=run_dir,
            udmi_root=self.udmi_root,
        )

    # --------------------------------------------------------------------------
    # Universal Intent Parser & Dispatcher
    # --------------------------------------------------------------------------

    def run_headless(self, prompt_or_file: str) -> str:
        """Executes any instruction or support bundle headless and returns output."""
        clean_input = prompt_or_file.strip()

        # Check if file path passed directly (e.g. support bundle or log)
        if os.path.isfile(clean_input):
            if clean_input.endswith(".zip") or clean_input.endswith(".tar.gz") or clean_input.endswith(".tgz"):
                return self._handle_bundle_triage(clean_input)
            elif clean_input.endswith(".json") and "metadata" in clean_input:
                return f"Inspecting metadata file: {clean_input}\n" + json.dumps(
                    inspect_site_model(os.path.dirname(os.path.dirname(os.path.dirname(clean_input)))), indent=2
                )

        return self.run(clean_input)

    def run(
        self,
        prompt: str,
        context: Optional[SessionContext] = None,
        stream_callback: Optional[Callable[[str], None]] = None,
        event_callback: Optional[Callable[[Dict[str, Any]], None]] = None,
        cancel_event: Optional[threading.Event] = None,
    ) -> str:
        """Executes a natural language prompt with stateful multi-turn conversational memory.

        `stream_callback` receives prose as it is generated. `event_callback`, when
        supplied, additionally receives structured records describing what the agent
        is doing: see `_run_llm` for the emitted record types. Both are optional and
        independent; the CLI passes neither, a plain streaming caller passes only the
        first, and a UI that needs to render phases and tool activity passes both.
        The structured channel exists so such a UI never has to scrape the prose.
        """
        q = prompt.strip()
        if context is not None:
            ctx_mgr = ContextManager(context=context, udmi_root=self.udmi_root)
        else:
            ctx_mgr = ContextManager(udmi_root=self.udmi_root)
            if self.active_session_id and not ctx_mgr.context.active_session_id:
                ctx_mgr.context.active_session_id = self.active_session_id

        ctx_mgr.add_user_message(q)

        # If LLM API credentials are configured, execute generative pipeline (fail fast on error)
        if self.config.provider in (ProviderType.VERTEX_AI, ProviderType.AI_STUDIO) or self.client is not None:
            res = self._run_llm(
                q,
                context_mgr=ctx_mgr,
                stream_callback=stream_callback,
                event_callback=event_callback,
                cancel_event=cancel_event,
            )
            ctx_mgr.add_assistant_message(res)
            return res

        res = self._run_deterministic(q, context_mgr=ctx_mgr, stream_callback=stream_callback)
        ctx_mgr.add_assistant_message(res)
        return res

    # --------------------------------------------------------------------------
    # Deterministic Execution Pipeline
    # --------------------------------------------------------------------------

    def _run_deterministic(
        self,
        query: str,
        context_mgr: Optional[ContextManager] = None,
        stream_callback: Optional[Callable[[str], None]] = None,
    ) -> str:
        """Deterministic, factual execution of queries with contextual antecedent resolution."""
        q_lower = query.lower()
        active_dev = context_mgr.context.active_device_id if context_mgr else None
        active_test = context_mgr.context.active_test_id if context_mgr else None
        active_site = context_mgr.context.active_site_model if context_mgr else self.active_site_model
        active_sess = context_mgr.context.active_session_id if context_mgr else self.active_session_id

        def emit(text: str) -> None:
            if stream_callback:
                stream_callback(text)

        # 1. Environment Management (Start / Ensure / Bring up / Spin up / Setup)
        if any(w in q_lower for w in ("start", "ensure", "provision", "bring up", "spin up", "launch", "setup", "deploy", "up ")) and (
            any(w in q_lower for w in ("stack", "environment", "local", "infra", "infrastructure", "setup", "sequencer"))
        ) and not ("why" in q_lower or "fail" in q_lower):
            emit("Mantis: Provisioning isolated environment...\n")
            test_id = self._extract_word(query, r"(?:environment|session|setup|stack)\s+['\"]?([a-zA-Z0-9_-]+)['\"]?", default=active_sess or "dev_1")
            dut_match = re.search(r"dut\s+([a-zA-Z0-9_-]+)", query, re.IGNORECASE)
            dut = dut_match.group(1) if dut_match else active_dev
            site_match = re.search(r"(sites/[a-zA-Z0-9_\-\./]+)", query)
            site = site_match.group(1) if site_match else active_site

            added = ["validator"] if "validator" in q_lower else None
            exclude = []
            for svc in ("udmis", "influxdb", "postgres", "butler"):
                if f"without {svc}" in q_lower or f"!{svc}" in q_lower:
                    exclude.append(svc)

            try:
                res = self.session_mgr.ensure_test_setup(
                    test_id=test_id,
                    site_model=site,
                    dut_device_id=dut,
                    exclude=exclude or None,
                    added=added,
                )
                self.active_session_id = test_id
                if context_mgr:
                    context_mgr.context.active_session_id = test_id
                    if dut:
                        context_mgr.context.active_device_id = dut
                    context_mgr.save_context()
                ports = res.get("ports", {})
                out = (
                    f"Mantis: Provisioning isolated environment '{res['session_name']}'...\n"
                    f"  * Allocated Port Block: MQTT={ports.get('mqtt')}, etcd={ports.get('etcd')}, "
                    f"influx={ports.get('influx')}, postgres={ports.get('postgres')}\n"
                    f"  * Launched Tmux Session: '{res['session_name']}' {res.get('windows', [])}\n"
                    f"  * Control Plane Ready: Mosquitto online, certificates generated, UDMIS ready.\n"
                )
                if dut:
                    out += f"  * DUT: {dut} running in window 'dut'.\n"
                out += f"Environment '{test_id}' is ready at {res.get('connection_url')}."
                emit(out)
                return out
            except Exception as e:
                err = f"Failed to start environment: {e}"
                emit(err)
                return err

        # 2. Environment Teardown (Stop / Terminate)
        if ("stop" in q_lower or "terminate" in q_lower or "kill" in q_lower) and (
            "environment" in q_lower or "stack" in q_lower or "session" in q_lower
        ):
            test_id = self._extract_word(query, r"(?:environment|session|setup)\s+['\"]?([a-zA-Z0-9_-]+)['\"]?", default=active_sess or "dev_1")
            res = self.session_mgr.terminate_test_setup(test_id)
            out = f"Environment '{test_id}' ({res.get('session_name')}) terminated."
            emit(out)
            return out

        # 3. Test Execution Triggering
        if "run " in q_lower and (" on " in q_lower or " for " in q_lower or "test " in q_lower) and not ("why" in q_lower or "fail" in q_lower):
            test_id = self._extract_word(query, r"run\s+(?:test\s+)?([a-z0-9_]{4,})", default=active_test)
            device_id = self._extract_word(
                query,
                r"(?:device\s+|dut\s+|(?:for|on)\s+device\s+)([A-Za-z0-9_-]{3,})|(?:for|on)\s+([A-Za-z0-9]+[-_][A-Za-z0-9_-]+)",
                default=active_dev,
            )
            target_spec = self._extract_word(query, r"(//[a-zA-Z0-9_\-\./:\+@]+)", default=None)
            site = self._extract_word(query, r"(sites/[a-zA-Z0-9_\-\./]+)", default=active_site or "sites/udmi_site_model")

            if not test_id or not device_id:
                out = "Mantis: Please specify both the test name and device ID to execute (e.g. `bin/mantis \"Run <test_name> for device <device_id>\"`)."
                emit(out)
                return out
            
            if target_spec:
                from mantis.tools.sequencer import run_sequencer_test
                res = run_sequencer_test(
                    session_mgr=self.session_mgr,
                    test_name=test_id,
                    device_id=device_id,
                    target_spec=target_spec,
                    site_model=site,
                )
                if context_mgr:
                    context_mgr.context.active_test_id = test_id
                    context_mgr.context.active_device_id = device_id
                    context_mgr.context.active_site_model = site
                    context_mgr.context.active_session_id = res.get("session_id")
                    context_mgr.save_context()
                out = (
                    f"Mantis: Launched sequencer test '{test_id}' for {device_id} against '{res.get('target_spec')}'...\n"
                    f"  * Session: '{res.get('session_id')}' (Window: 'sequencer')\n"
                    f"  * Command: {res.get('command')}\n"
                    f"Use `/logs sequencer` to inspect live progress or ask 'Why did it fail?' for root-cause diagnosis."
                )
                emit(out)
                return out

            target_sess = active_sess or self.active_session_id
            if not target_sess:
                active_setups = self.session_mgr.list_test_setups()
                if active_setups:
                    target_sess = active_setups[0].get("test_id")

            if context_mgr:
                context_mgr.context.active_test_id = test_id
                context_mgr.context.active_device_id = device_id
                context_mgr.context.active_site_model = site
                if target_sess:
                    context_mgr.context.active_session_id = target_sess
                context_mgr.save_context()

            if target_sess and self.session_mgr.is_session_active(self.session_mgr.sanitize_session_name(target_sess)):
                sess_info = self.session_mgr.get_session_info(target_sess) or {}
                ports = sess_info.get("ports", {})
                mqtt_port = ports.get("mqtt", self.session_mgr.derive_port_block(target_sess))
                project_spec = sess_info.get("project_spec", f"//mqtt/localhost:{mqtt_port}")
                
                cmd = f"bin/sequencer '{site}' '{project_spec}' '{device_id}' '{test_id}'"
                try:
                    from mantis.tools.process import start_session_process
                    start_session_process(
                        session_mgr=self.session_mgr,
                        test_id=target_sess,
                        window="sequencer",
                        command=cmd,
                    )
                    out = (
                        f"Mantis: Launched sequencer test '{test_id}' for {device_id} in session '{target_sess}'...\n"
                        f"  * Semantic Window: 'sequencer'\n"
                        f"  * Target Spec: {project_spec}\n"
                        f"  * Command: {cmd}\n"
                        f"Use `/logs sequencer` to inspect live progress or ask 'Why did it fail?' for root-cause diagnosis."
                    )
                except Exception as e:
                    out = f"Mantis: Failed to launch sequencer in session '{target_sess}': {e}"
            else:
                out = (
                    f"Mantis: Target environment session '{target_sess or 'dev_1'}' is not running.\n"
                    f"Start an isolated environment first (e.g. `bin/mantis \"Start isolated local environment with DUT {device_id}\"`)."
                )
            emit(out)
            return out

        # 4. Diagnostic Triage / Root Cause Analysis
        if "why did" in q_lower or "fail" in q_lower or "diagnos" in q_lower or "triage" in q_lower:
            device_id = self._extract_word(
                query,
                r"(?:device\s+|dut\s+|(?:for|on)\s+device\s+)([A-Za-z0-9_-]{3,})|(?:for|on)\s+([A-Za-z0-9]+[-_][A-Za-z0-9_-]+)",
                default=active_dev,
            )
            test_id = self._extract_word(
                query,
                r"(?:did|run\s+(?:test\s+)?|test\s+)([a-z0-9_]{4,})",
                default=active_test,
            )
            site = self._extract_word(query, r"(sites/[a-zA-Z0-9_\-\./]+)", default=active_site or "sites/udmi_site_model")

            if not device_id or not test_id:
                out = "Mantis: Please specify both the device ID and test name to diagnose (e.g. `bin/mantis \"Diagnose test <test_name> on device <device_id>\"`)."
                emit(out)
                return out

            if context_mgr:
                context_mgr.context.active_device_id = device_id
                context_mgr.context.active_test_id = test_id
                context_mgr.context.active_site_model = site

            emit(f"Mantis: Analyzing logs, timestamps, and schema definitions for {device_id} / {test_id}...\n\n")
            diag = self.diagnose_test_failure(
                test_id=test_id,
                device_id=device_id,
                site_model=site,
            )
            report = diag.get("report", "")
            emit(report)
            return report

        # 5. Schema Inspection
        if "schema" in q_lower or "fields in" in q_lower or "inspect schema" in q_lower:
            schema_name = self._extract_word(query, r"([a-z0-9_]+)(?:\.json|\s+schema)", default="pointset")
            res = inspect_udmi_schema(schema_name=schema_name)
            if res.get("status") == "SUCCESS":
                reqs = res.get("required", [])
                props = list(res.get("schema", {}).get("properties", {}).keys()) if isinstance(res.get("schema"), dict) else []
                out = (
                    f"### Schema: `{res.get('schema_name')}`\n"
                    f"* **Title**: {res.get('title')}\n"
                    f"* **Description**: {res.get('description') or 'Authoritative UDMI schema'}\n"
                    f"* **Required Fields**: {reqs if reqs else '(None required)'}\n"
                    f"* **Properties**: {', '.join(props[:20]) if props else 'N/A'}\n"
                    f"* **File**: `{res.get('schema_file')}`"
                )
            else:
                out = f"Error inspecting schema: {res.get('error')}"
            emit(out)
            return out

        # 6. Site Model Inspection & Validation
        if "site model" in q_lower or "validate site" in q_lower or "inspect device" in q_lower:
            site = self._extract_word(query, r"(sites/[a-zA-Z0-9_\-\./]+)", default=active_site)
            device = self._extract_word(query, r"(?:device|for)\s+([A-Z0-9_-]{3,})", default=active_dev)
            res = inspect_site_model(site_model=site, device_id=device)
            if res.get("status") == "SUCCESS":
                if device:
                    out = (
                        f"### Device Metadata: `{device}`\n"
                        f"* **Site Model**: `{site}`\n"
                        f"* **Points Defined**: {res.get('point_count')} points ({', '.join(res.get('points', [])[:10])})\n"
                        f"* **System**: {json.dumps(res.get('system', {}))}\n"
                        f"* **Gateway**: {res.get('gateway', {}).get('gateway_id', 'Direct device')}"
                    )
                else:
                    out = (
                        f"### Site Model: `{site}`\n"
                        f"* **Total Devices**: {res.get('device_count')}\n"
                        f"* **Devices**: {', '.join(res.get('devices', [])[:15])}\n"
                        f"* **Status**: Valid site model structure"
                    )
            else:
                out = f"Error inspecting site model: {res.get('error')}"
            emit(out)
            return out

        # 7. Configuration & Metadata Patching
        if "set " in q_lower or "patch" in q_lower or "update " in q_lower:
            device = self._extract_word(
                query,
                r"(?:device\s+|dut\s+|(?:for|on)\s+device\s+)([A-Za-z0-9_-]{3,})|(?:for|on)\s+([A-Za-z0-9]+[-_][A-Za-z0-9_-]+)",
                default=active_dev,
            )
            site = self._extract_word(query, r"((?:sites/|/)[a-zA-Z0-9_\-\./]+)", default=active_site or "sites/udmi_site_model")
            patch_data = self._parse_patch_data(query)

            if not device:
                out = "Mantis: Please specify the device ID to patch (e.g. `bin/mantis \"Set sample_rate_sec to 10 for device <device_id>\"`)."
                emit(out)
                return out

            if patch_data:
                res = patch_site_model(
                    site_model=site,
                    device_id=device,
                    patch_data=patch_data,
                    udmi_root=self.udmi_root,
                )
                if res.get("status") in ("SUCCESS", "PATCHED"):
                    out = (
                        f"### Applied Configuration Patch for `{device}`\n"
                        f"* **File**: `{res.get('file')}`\n"
                        f"* **Backup Created**: `{res.get('backup')}`\n"
                        f"* **Diff**:\n```diff\n{res.get('diff')}```"
                    )
                else:
                    out = f"Error applying configuration patch: {res.get('error')}"
            else:
                out = f"Could not parse configuration patch fields from '{query}'."
            emit(out)
            return out

        # 8. Multi-Run Evaluation & Stability
        if "eval" in q_lower or "stability" in q_lower:
            from mantis.tools.diagnostics import evaluate_test_stability
            res = evaluate_test_stability(site_model=active_site, udmi_root=self.udmi_root)
            out = res.get("summary_report", "")
            emit(out)
            return out

        # 9. Differential Comparison
        if "compare" in q_lower or "diff" in q_lower:
            runs = discover_test_runs(udmi_root=self.udmi_root)
            target = runs[0]["path"] if runs else "out"
            res = compare_test_runs(target_run=target)
            out = f"### Behavioral Differential Analysis\n\n{res.get('differential_table', '')}"
            emit(out)
            return out

        # 10. Golden Baseline & Anti-Cheating Verification
        if "golden" in q_lower or "baseline" in q_lower or "anti-cheating" in q_lower or "anti cheating" in q_lower:
            from mantis.tools.golden import verify_golden_baseline
            m = re.search(r"(?:for|baseline|golden)\s+(?:baseline\s+)?(?:for\s+)?['\"]?([a-zA-Z0-9_\-\.]+)", query, re.IGNORECASE)
            baseline_name = m.group(1).strip() if m else "validator"
            if baseline_name.lower() in ("golden", "baseline", "for", "with", "the", "a", "an"):
                baseline_name = "validator"
            baseline_name = os.path.splitext(os.path.basename(baseline_name))[0]
            emit(f"Mantis: Verifying golden baseline for '{baseline_name}' with anti-cheating audit...\n")
            res = verify_golden_baseline(baseline_name=baseline_name, udmi_root=self.udmi_root)
            out = res.get("report", "")
            emit(out)
            return out

        # 11. Architecture & Topology Diagrams
        if "topology" in q_lower or ("diagram" in q_lower and ("site" in q_lower or "arch" in q_lower or "device" in q_lower or "network" in q_lower)):
            from mantis.tools.visualization import generate_topology_diagram
            site = self._extract_word(query, r"(sites/[a-zA-Z0-9_\-\./]+)", default=active_site)
            device = self._extract_word(query, r"(?:device|for)\s+([A-Z0-9_-]{3,})", default=active_dev)
            fmt = "both"
            if "mermaid" in q_lower and "dot" not in q_lower:
                fmt = "mermaid"
            elif "dot" in q_lower and "mermaid" not in q_lower:
                fmt = "dot"
            emit(f"Mantis: Generating site topology diagram for '{site}'...\n")
            res = generate_topology_diagram(site_model=site, focus_device=device, format=fmt, udmi_root=self.udmi_root)
            if res.get("status") == "SUCCESS":
                out = (
                    f"### Site Topology: `{site}` ({res.get('device_count')} devices, {res.get('gateway_count')} gateways)\n\n"
                    f"{res.get('rendered')}"
                )
            else:
                out = f"Error generating topology diagram: {res.get('error')}"
            emit(out)
            return out

        # 12. Sequence Diagrams
        if "sequence diagram" in q_lower or ("diagram" in q_lower and ("sequence" in q_lower or "run" in q_lower or "flow" in q_lower or "timeline" in q_lower)):
            from mantis.tools.visualization import generate_sequence_diagram
            runs = discover_test_runs(udmi_root=self.udmi_root)
            run_dir = runs[0]["path"] if runs else os.path.join(self.udmi_root, "out")
            fmt = "both"
            if "mermaid" in q_lower and "dot" not in q_lower:
                fmt = "mermaid"
            elif "dot" in q_lower and "mermaid" not in q_lower:
                fmt = "dot"
            emit(f"Mantis: Generating sequence diagram from '{run_dir}'...\n")
            res = generate_sequence_diagram(run_dir=run_dir, format=fmt, udmi_root=self.udmi_root)
            if res.get("status") == "SUCCESS":
                out = f"### Sequence Execution Flow\n\n{res.get('rendered')}"
            else:
                out = f"Error generating sequence diagram: {res.get('error')}"
            emit(out)
            return out

        # 13. Codebase & Documentation Exploration
        if any(w in q_lower for w in ("doc for", "guide for", "spec for", "locate doc", "find spec")):
            from mantis.tools.codebase import locate_udmi_doc
            topic = self._extract_word(query, r"(?:for|spec|doc|guide)\s+([a-zA-Z0-9_\-\.]+)", default="writeback")
            res = locate_udmi_doc(topic=topic, udmi_root=self.udmi_root)
            docs = res.get("documents", []) if isinstance(res, dict) else []
            if docs:
                lines = [f"### UDMI Documentation for '{topic}':"]
                for d in docs:
                    lines.append(f"* **[{d['title']}](file://{d['file']})** (`{d['file']}`)\n  {d.get('preview', '')}")
                out = "\n".join(lines)
            else:
                out = f"No documentation found for topic '{topic}'."
            emit(out)
            return out

        if "search" in q_lower or "grep" in q_lower:
            from mantis.tools.codebase import search_codebase
            m = re.search(r"(?:search|grep)\s+(?:for\s+)?['\"]?([^'\"]+)['\"]?", query, re.IGNORECASE)
            term = m.group(1).strip() if m else query
            res = search_codebase(query=term, max_results=10, udmi_root=self.udmi_root)
            matches = res.get("matches", []) if isinstance(res, dict) else []
            if matches:
                lines = [f"### Codebase search results for '{term}':"]
                for r in matches[:10]:
                    lines.append(f"* `{r['file']}:{r['line']}`: {r.get('snippet', '')}")
                out = "\n".join(lines)
            else:
                out = f"No matches found for '{term}'."
            emit(out)
            return out

        if "inspect test" in q_lower or "sequencer test" in q_lower:
            from mantis.tools.codebase import inspect_sequencer_test
            test_name = self._extract_word(query, r"(?:test|for)\s+([a-z0-9_]{4,})", default=active_test or "pointset_publish")
            res = inspect_sequencer_test(test_name=test_name, udmi_root=self.udmi_root)
            if res.get("status") == "SUCCESS":
                out = (
                    f"### Sequencer Test: `{res['test_name']}`\n"
                    f"* **Class**: `{res['class_name']}` ({res['file_path']})\n"
                    f"* **Features**: {', '.join(res.get('features', []))}\n"
                    f"* **Assertions**: {', '.join(res.get('assertions', []))}\n"
                    f"* **Code Snippet**:\n```java\n{res.get('method_code', '')[:800]}\n```"
                )
            else:
                out = f"Error inspecting sequencer test: {res.get('error')}"
            emit(out)
            return out

        # 14. Message Traces & Log Anomalies
        if "trace" in q_lower or "payload" in q_lower:
            from mantis.tools.artifacts import inspect_message_trace
            runs = discover_test_runs(udmi_root=self.udmi_root)
            run_dir = runs[0]["path"] if runs else os.path.join(self.udmi_root, "out")
            res = inspect_message_trace(run_dir=run_dir, udmi_root=self.udmi_root)
            if res:
                out = f"### Recorded Message Traces in `{run_dir}` ({len(res)} traces found)\n"
                for t in res[:5]:
                    out += f"* **{t['message_type']}** (`{t['trace_file']}`):\n```json\n{json.dumps(t['payload'], indent=2)[:300]}\n```\n"
            else:
                out = f"No message traces found in `{run_dir}`."
            emit(out)
            return out

        if "anomal" in q_lower:
            from mantis.tools.artifacts import detect_log_anomalies
            runs = discover_test_runs(udmi_root=self.udmi_root)
            run_dir = runs[0]["path"] if runs else os.path.join(self.udmi_root, "out")
            res = detect_log_anomalies(run_dir=run_dir, udmi_root=self.udmi_root)
            if res.get("status") == "SUCCESS":
                anoms = res.get("anomalies", [])
                out = f"### Log Anomaly Analysis for `{run_dir}` ({len(anoms)} anomalies detected)\n"
                for a in anoms:
                    out += f"* **[{a['severity']}] {a['type']}**: {a['message']}\n"
            else:
                out = f"Error detecting anomalies: {res.get('error')}"
            emit(out)
            return out

        # Default fallback: Answer with skill references
        skills_summary = self.skills.get_system_prompt_catalog()
        out = (
            f"Mantis processed query: '{query}'\n\n"
            f"{skills_summary}\n\n"
            f"Use `bin/mantis \"<instruction>\"` for test triage, provisioning, schema queries, or site model patching."
        )
        emit(out)
        return out

    def classify_intent_tier(self, prompt: str) -> ModelTier:
        """Classifies user intent into Flash Tier (fast lookups, schemas, log slicing, entities)
        or Pro Tier (deep diagnostic reasoning, adversarial critique, differential analysis, patching)."""
        p = prompt.strip().lower()

        # Indicators only match at word boundaries. Plain substring matching sent
        # questions about a "catalog" or a "technology" to the Flash tier because they
        # contain "log", which skipped scoping and with it the informational path.
        # Letters and digits are word characters here; '_', '.', '/' and '-' are
        # separators, so identifiers such as `sequence.log` or `udmi_site_model`
        # still expose their component words.
        def matches(stem: str, whole_word: bool) -> bool:
            tail = r"(?![a-z0-9])" if whole_word else ""
            return re.search(r"(?<![a-z0-9])" + re.escape(stem) + tail, p) is not None

        # Explanation questions run on PRO so the scoping turn happens and the plan can
        # declare INFORMATIONAL_SCOPE, which is what issues INFORMATIONAL_DIRECTIVE.
        explanation_indicators = [
            "explain", "how does", "how do", "what does", "what is", "describe",
            "should my device",
        ]
        if any(matches(ind, whole_word=True) for ind in explanation_indicators):
            return ModelTier.PRO

        # Pro indicators (deep reasoning, failure diagnosis, diffs, patches, triage, adversarial critique).
        # These are stems: they match at the start of a word ("fail" -> "failed").
        pro_indicators = [
            "why", "diagnos", "root cause", "fail", "error", "triage", "troubleshoot",
            "diff", "compare", "patch", "fix", "remediat", "critic", "adversar",
            "investigat", "correlat", "post-mortem", "broken", "divergence",
            "provision", "ensure", "sequencer", "run test", "start stack",
            "golden", "baseline", "anti-cheating"
        ]
        if any(matches(ind, whole_word=False) for ind in pro_indicators):
            return ModelTier.PRO

        # Flash indicators (simple schema lookups, log slicing, entity extraction, status checks, listing).
        # Whole words only; plural forms are listed explicitly.
        flash_indicators = [
            "schema", "schemas", "list schema", "inspect schema",
            "log", "logs", "slice log", "extract log", "tail log", "show log",
            "extract", "entities", "metadata", "list runs", "list devices", "devices", "show devices",
            "show site", "inspect site", "site_model", "site model", "status", "version", "help",
            "summarize", "condense", "lookup"
        ]
        if any(matches(ind, whole_word=True) for ind in flash_indicators):
            return ModelTier.FLASH

        # Default to PRO for open-ended or complex reasoning
        return ModelTier.PRO

    async def run_async(
        self,
        prompt: str,
        context: Optional[SessionContext] = None,
        stream_callback: Optional[Callable[[str], None]] = None,
    ) -> str:
        """Asynchronously executes a natural language prompt with non-blocking execution."""
        import asyncio
        loop = asyncio.get_running_loop()
        return await loop.run_in_executor(
            None, self.run, prompt, context, stream_callback
        )

    # --------------------------------------------------------------------------
    # Generative AI Execution Pipeline
    # --------------------------------------------------------------------------

    def _call_api_with_retry(
        self,
        client_models: Any,
        method_name: str,
        model: str,
        contents: Any,
        config: Any,
        max_retries: int = 3,
        base_delay: float = 0.5,
    ) -> Any:
        """Execute client model calls with exponential backoff retry for transient rate limits and network errors."""
        import random
        import time

        last_err = None
        for attempt in range(max_retries):
            try:
                method = getattr(client_models, method_name)
                return method(model=model, contents=contents, config=config)
            except Exception as e:
                last_err = e
                err_str = str(e).lower()

                is_retryable = False
                status_code = getattr(e, "code", None) or getattr(e, "status_code", None)
                if status_code in (429, 503, 504):
                    is_retryable = True
                elif isinstance(e, (ConnectionError, TimeoutError)):
                    is_retryable = True
                else:
                    try:
                        from google.genai.errors import APIError as GenaiAPIError  # type: ignore
                        if isinstance(e, GenaiAPIError) and e.code in (429, 503, 504):
                            is_retryable = True
                    except ImportError:
                        pass
                    try:
                        from google.api_core.exceptions import (  # type: ignore
                            TooManyRequests,
                            ServiceUnavailable,
                            ResourceExhausted,
                            DeadlineExceeded,
                        )
                        if isinstance(e, (TooManyRequests, ServiceUnavailable, ResourceExhausted, DeadlineExceeded)):
                            is_retryable = True
                    except ImportError:
                        pass

                if not is_retryable:
                    if any(
                        w in err_str
                        for w in ("429", "503", "resource_exhausted", "quota exceeded", "rate limit")
                    ):
                        is_retryable = True

                if attempt < max_retries - 1 and is_retryable:
                    jitter = random.uniform(0.8, 1.2)
                    sleep_time = (base_delay * (2**attempt)) * jitter
                    time.sleep(sleep_time)
                    continue
                raise
        if last_err:
            raise last_err

    def _resolve_required_subsystems(self, plan_text: str) -> Tuple[List[str], List[str]]:
        """Parses REQUIRED_SUBSYSTEMS from a scoping plan and resolves it against the repo.

        Returns (resolved, rejected). Directories that do not exist are rejected rather
        than silently trusted, so a hallucinated name never becomes a coverage target.
        """
        resolved: List[str] = []
        rejected: List[str] = []
        match = re.search(r"REQUIRED_SUBSYSTEMS\s*:\s*(.+)", plan_text, re.IGNORECASE)
        if not match:
            return resolved, rejected
        for raw_item in re.split(r"[,\n]+", match.group(1)):
            candidate = raw_item.strip().strip("`\'\"*[]").lstrip("/")
            if not candidate:
                continue
            top_dir = candidate.split("/")[0]
            if top_dir in resolved or top_dir in rejected:
                continue
            if os.path.isdir(os.path.join(self.udmi_root, top_dir)):
                resolved.append(top_dir)
            else:
                rejected.append(top_dir)
        return resolved, rejected

    def _parse_failure_scope(self, plan_text: str) -> Optional[str]:
        """Returns the FAILURE_SCOPE a scoping plan declared, or None if it declared none
        of the recognized scopes."""
        alternatives = "|".join(FAILURE_SCOPES)
        match = re.search(
            rf"FAILURE_SCOPE\s*:\s*[`*\[<]*\s*({alternatives})\b", plan_text, re.IGNORECASE
        )
        return match.group(1).upper() if match else None

    def _parse_competing_hypotheses(self, plan_text: str) -> List[str]:
        """Parses the labelled hypotheses the Actor committed to during scoping.

        Only lines of the form '- H<n>: <text>' under COMPETING_HYPOTHESES are
        recognized. Requiring the explicit label keeps the later resolution check a
        structural match rather than a fuzzy comparison of restated prose.
        """
        block = re.search(
            r"COMPETING_HYPOTHESES\s*:\s*(.*?)(?:\n\s*REQUIRED_SUBSYSTEMS\s*:|\Z)",
            plan_text,
            re.IGNORECASE | re.DOTALL,
        )
        if not block:
            return []
        hypotheses: List[str] = []
        for line in block.group(1).splitlines():
            match = re.match(r"\s*[-*]?\s*H(\d+)\s*[:.)-]\s*(.+)", line.strip())
            if match:
                hypotheses.append(match.group(2).strip())
        return hypotheses

    def _hypothesis_verdicts(
        self, answer_text: str, hypothesis_count: int
    ) -> Dict[int, Optional[str]]:
        """Extracts the verdict rendered for each committed hypothesis.

        Returns a mapping of 1-based label to verdict, or None where the answer
        rendered no verdict at all for that hypothesis.
        """
        verdicts: Dict[int, Optional[str]] = {}
        alternatives = "|".join(HYPOTHESIS_VERDICTS)
        for index in range(1, hypothesis_count + 1):
            # The verdict must belong to THIS hypothesis: no other H<n> label may
            # appear between the label and the verdict, otherwise a neighbouring
            # hypothesis's verdict would satisfy this one. The scan spans lines
            # because the audit is normally rendered as markdown, with the verdict
            # on a nested bullet underneath the restated hypothesis rather than
            # inline with it; a single-line scan silently marked every such answer
            # as unresolved and sent a correctly audited answer back for rework.
            pattern = rf"H{index}\b(?:(?!\bH\d)[\s\S]){{0,400}}?\b({alternatives})\b"
            match = re.search(pattern, answer_text, re.IGNORECASE)
            verdicts[index] = match.group(1).upper() if match else None
        return verdicts

    def _runtime_evidence_declarations(
        self, answer_text: str, hypothesis_count: int
    ) -> Dict[int, Optional[str]]:
        """Extracts the runtime-evidence tier declared for each committed hypothesis.

        Returns a mapping of 1-based label to declared tier, or None where the answer
        made no declaration. As with verdicts, no other H<n> label may intervene
        between the label and its declaration, so a neighbour's declaration cannot be
        borrowed. Unlike verdicts the match may span lines, because the declaration is
        normally written underneath the hypothesis rather than inline.
        """
        declarations: Dict[int, Optional[str]] = {}
        alternatives = "|".join(RUNTIME_EVIDENCE_TIERS)
        for index in range(1, hypothesis_count + 1):
            pattern = (
                rf"H{index}\b(?:(?!\bH\d)[\s\S]){{0,800}}?"
                rf"RUNTIME_EVIDENCE\s*:\s*({alternatives})\b"
            )
            match = re.search(pattern, answer_text, re.IGNORECASE)
            declarations[index] = match.group(1).upper() if match else None
        return declarations

    def _audit_violations(
        self,
        answer_text: str,
        hypothesis_count: int,
        hypothesis_subsystems: Optional[Dict[int, List[str]]] = None,
        read_subsystems: Optional[set] = None,
        obtained_evidence_tiers: Optional[set] = None,
    ) -> List[str]:
        """Returns the reasons an answer's hypothesis audit is unacceptable.

        Four distinct failures are caught. First, a hypothesis left with no verdict:
        the Actor raises a correct hypothesis during scoping and then abandons it.
        Second, a non-discriminating audit: marking every hypothesis as a cause
        excludes nothing and is as unfalsifiable as dropping them, so exactly one
        must be ranked PRIMARY. Third, a decisive verdict on a subsystem whose source
        was never read, which launders an assumption into an authoritative finding.
        Fourth, a verdict on backend behavior that does not say whether it rests on
        runtime logs or on reading the source. Arguing a backend cause from source
        alone is legitimate and often unavoidable for an older incident, but it must
        be labelled as such rather than narrated as an observation.
        """
        if hypothesis_count <= 0:
            return []
        verdicts = self._hypothesis_verdicts(answer_text, hypothesis_count)
        violations: List[str] = []

        missing = [index for index, verdict in verdicts.items() if verdict is None]
        if missing:
            violations.append(
                f"No verdict given for {', '.join(f'H{i}' for i in missing)}. Every "
                f"hypothesis needs one of: {', '.join(HYPOTHESIS_VERDICTS)}."
            )

        primaries = [index for index, verdict in verdicts.items() if verdict == "PRIMARY"]
        if len(primaries) > 1:
            violations.append(
                f"{', '.join(f'H{i}' for i in primaries)} are all marked PRIMARY. Exactly one "
                f"hypothesis may be PRIMARY; rank the others CONTRIBUTING or REFUTED."
            )
        elif not primaries and not missing:
            violations.append(
                "No hypothesis is marked PRIMARY. Identify which one is the root cause, or "
                "mark it UNRESOLVED and state what evidence would settle it."
            )

        # A decisive verdict requires having looked. Grepping a directory is not
        # examining it: the Actor refuted a backend hypothesis on the strength of a
        # single search and zero file reads, asserting specific runtime behavior of
        # code it never opened. UNRESOLVED remains available when it has not looked.
        for index, verdict in verdicts.items():
            if verdict not in ("PRIMARY", "REFUTED"):
                continue
            named = hypothesis_subsystems.get(index, []) if hypothesis_subsystems else []
            unread = [s for s in named if s not in (read_subsystems or set())]
            if unread:
                violations.append(
                    f"H{index} is marked {verdict} but you never read any source in "
                    f"{', '.join(unread)}, which is the subsystem it is about. Read that code "
                    f"before ruling on it, or mark H{index} UNRESOLVED."
                )

        # Backend conclusions must state their evidentiary basis. The tier itself is
        # not constrained: NONE is acceptable and expected once an incident outlives
        # its logs. What is rejected is leaving the basis unstated.
        declarations = self._runtime_evidence_declarations(answer_text, hypothesis_count)
        for index, verdict in verdicts.items():
            if verdict is None or verdict == "UNRESOLVED":
                continue
            named = hypothesis_subsystems.get(index, []) if hypothesis_subsystems else []
            backend = [s for s in named if s in BACKEND_SUBSYSTEMS]
            if not backend:
                continue
            declared = declarations.get(index)
            if declared is None:
                violations.append(
                    f"H{index} is marked {verdict} and concerns {', '.join(backend)}, whose "
                    f"behavior is only visible at runtime, but it declares no evidence basis. "
                    f"Add a line 'RUNTIME_EVIDENCE: "
                    f"{' | '.join(RUNTIME_EVIDENCE_TIERS)}' to H{index}, using the tier "
                    f"get_udmis_runtime_logs reported. NONE is acceptable: it states that the "
                    f"logs proving this no longer exist and the mechanism is read from source."
                )
            elif declared == "NONE" and RUNTIME_EVIDENCE_NONE_QUALIFIER not in answer_text.lower():
                violations.append(
                    f"H{index} declares RUNTIME_EVIDENCE: NONE without its qualifier. Write "
                    f"'RUNTIME_EVIDENCE: NONE ({RUNTIME_EVIDENCE_NONE_QUALIFIER})' so the "
                    f"reader can tell this conclusion is read from the source rather than "
                    f"observed in logs."
                )
            elif declared in ("LOCAL_FILE", "CLOUD") and declared not in (
                obtained_evidence_tiers or set()
            ):
                # The declaration is checkable, so it is checked. A model that has
                # read no logs will still narrate source analysis as observation if
                # only a prompt forbids it.
                obtained = (
                    ", ".join(sorted(obtained_evidence_tiers))
                    if obtained_evidence_tiers
                    else "none"
                )
                violations.append(
                    f"H{index} declares RUNTIME_EVIDENCE: {declared}, but no "
                    f"get_udmis_runtime_logs call returned that tier during this "
                    f"investigation (tiers actually obtained: {obtained}). Either cite the "
                    f"log content you retrieved, or declare 'RUNTIME_EVIDENCE: NONE "
                    f"({RUNTIME_EVIDENCE_NONE_QUALIFIER})'."
                )
        return violations

    def _hypothesis_subsystems(
        self, hypotheses: List[str], known_subsystems: List[str]
    ) -> Dict[int, List[str]]:
        """Maps each hypothesis to the contracted subsystems it explicitly names.

        Matching is a literal word-boundary search for directory names the scoping
        step already resolved against the repository, so no inference is involved.
        """
        mapping: Dict[int, List[str]] = {}
        for index, text in enumerate(hypotheses, start=1):
            named = [
                subsystem
                for subsystem in known_subsystems
                if re.search(rf"\b{re.escape(subsystem)}\b", text, re.IGNORECASE)
            ]
            mapping[index] = named
        return mapping



    def _append_model_turn(self, contents, types, parts, emit) -> bool:
        """Appends a model turn, refusing to replay one that carries no content.

        A streamed turn can coalesce to nothing when the model emits only blank text
        parts. Replaying that sends a Content with no parts, which Vertex rejects
        outright ('must include at least one parts field'), aborting the run and
        handing control to the fallback engine. The empty turn is dropped and
        reported instead: history stays valid and the loop can continue.
        """
        usable = [part for part in parts if _part_carries_content(part)]
        if not usable:
            emit(
                "\n[Mantis Warning] The model returned a turn with no usable content. "
                "It is not being replayed as history.\n"
            )
            return False
        contents.append(types.Content(role="model", parts=usable))
        return True

    def _run_llm(
        self,
        prompt: str,
        context_mgr: Optional[ContextManager] = None,
        stream_callback: Optional[Callable[[str], None]] = None,
        max_steps: int = 30,
        tier: Optional[ModelTier] = None,
        enable_tripartite: Optional[bool] = None,
        enable_scoping: Optional[bool] = None,
        event_callback: Optional[Callable[[Dict[str, Any]], None]] = None,
        cancel_event: Optional[threading.Event] = None,
    ) -> str:
        """Executes multi-step ReAct planning using google-genai SDK, token metrics, and dynamic tool execution.

        When `event_callback` is supplied it receives structured records mirroring the
        prose already written to `stream_callback`. A consumer gets the same facts
        without pattern-matching strings like '[Mantis ReAct Step 3]', which would
        break the moment that wording changed. Record `type` is one of:

          phase        {"phase": SCOPING|ACTOR|CRITIC|ARBITRATOR}
          hypotheses   {"hypotheses": [str]}            the plan's committed H1..Hn
          tool_call    {"call_id", "tool", "args", "step"}
          tool_result  {"call_id", "tool", "status", "output"}
          audit        {"hypotheses": [{"label","hypothesis","verdict","rationale",
                        "evidence_tier"}], "audit_section": str|None}
          metrics      {"duration_sec", "steps", "tool_calls", "tripartite_status"}

        Emission is best-effort: a raising callback is logged and swallowed, because a
        consumer's rendering bug must not abort an investigation that is already running.
        """
        import time
        from google import genai  # type: ignore
        from google.genai import types  # type: ignore
        from mantis.models import ExecutionMetrics
        from mantis.tools.registry import get_genai_tools, execute_tool

        start_time = time.time()
        metrics = ExecutionMetrics()
        tool_executions: List[Dict[str, Any]] = []

        def emit(text: str) -> None:
            if stream_callback:
                stream_callback(text)

        def notify(record_type: str, **fields: Any) -> None:
            if not event_callback:
                return
            try:
                event_callback({"type": record_type, **fields})
            except Exception as callback_error:  # never let a consumer abort the run
                logger.warning(
                    "event_callback raised on %s record: %s", record_type, callback_error
                )

        def check_cancel(boundary: str) -> None:
            # Observed only at step boundaries; see MantisCancelled.
            if cancel_event is not None and cancel_event.is_set():
                emit(f"\n[Mantis] Stopped by operator before {boundary}.\n")
                raise MantisCancelled(f"Stopped by operator before {boundary}.")

        if self.client:
            client = self.client
        elif self.config.provider == ProviderType.VERTEX_AI:
            project = os.getenv("GOOGLE_CLOUD_PROJECT", os.getenv("GCP_PROJECT", self.config.default_gcp_project))
            location = os.getenv("GOOGLE_CLOUD_REGION", os.getenv("GCP_REGION", self.config.default_gcp_location))
            client = genai.Client(vertexai=True, project=project, location=location)
        else:
            client = genai.Client()

        skills_catalog = self.skills.get_system_prompt_catalog()
        relevant_skills = self.skills.load_skills_for_context(prompt)

        system_instruction = f"""You are Mantis, the autonomous diagnostic agent and management control plane for the Universal Device Management Interface (UDMI).

{skills_catalog}

{relevant_skills}

UDMI Architecture & Test Conventions:
- **Site Model (`site_model`)**: ALWAYS a local directory path (e.g. `sites/udmi_site_model` or `sites/faucetsdn`), NEVER a URI starting with `//`. Default is `sites/udmi_site_model`.
- **Target Spec (`target_spec`)**: Target cloud or broker connection spec:
  * Direct MQTT: `//mqtt/<host>[:<port>][/<namespace>]` (e.g. `//mqtt/localhost:18833`).
  * Cloud Reflector: `//gbos/<project_id>[/<namespace>]` (routes through ClearBlade / `UDMI-REFLECT` registry).
  * Direct Pub/Sub Reflector: `//gref/<project_id>[/<namespace>]+<user_name>` (direct Google Cloud Pub/Sub route).
- **Test Execution**: To run sequencer tests, call `run_sequencer_test(test_name=..., device_id=..., target_spec=..., site_model=...)`.
- **Cloud vs Local**: Cloud endpoints (`//gbos/...`, `//gref/...`, `//iotcore/...`) do NOT require `ensure_test_setup` (which is only for local Docker/tmux infrastructure). `run_sequencer_test` executes against remote cloud endpoints directly.

Investigation Strategy Guidelines:
- **Answer "did the device respond?" first**: For any failing sequencer test, establish
  before anything else whether the device under test published a message of its own
  during the run. `extract_timeline` reports this as `device_responded`, with the payload
  files it inspected. A device that sent nothing explains a timeout completely, and no
  theory about schemas, cadence, or firmware can be evaluated until this is settled.
  Note that `configAcked` is injected by the backend: a state payload containing only
  that proves the backend spoke, not the device.
- **Never guess the device's nature**: You cannot tell from run artifacts whether a
  device is physical hardware or an emulator (pubber). Unplugged hardware and an
  emulator that was never started are identical on the wire. Report what the evidence
  shows -- that the device did not respond -- and give remediation that covers both.
  The device under test is external to this repository and may have been built from
  the UDMI documentation alone: never cite pubber source as evidence of its behavior.
- **Absence of an error is not evidence of health**: A log records what was attempted.
  A device that never connects produces no transport error, no authentication failure,
  and no schema violation. Never write that a subsystem is healthy, reachable, or
  authenticated because its errors are missing; say the hypothesis was not assessed and
  name what would settle it.
- **Read the logs before reading the source**: Run directories contain `sequence.log`,
  `device_system.log`, and the message payloads. `search_codebase` reads `.log` files,
  but does not descend into run-output directories unless you pass one as `path_prefix`;
  it returns those it skipped in `skipped_artifact_dirs`. Source code shows what a
  component was meant to do, not what it did.
- **Generalizing Searches**: When diagnosing external errors, recognize that literal error strings from external systems may not exist in the local repository. Generalize searches to the underlying transport, messaging, and service layers using tools like `search_codebase` and `read_udmi_file`.
- **RCA Reporting Standard**: When diagnosing complex multi-component or transport failures, structure the diagnosis using a comprehensive Root Cause Analysis (RCA) format:
  1. TL;DR (Executive summary of root cause and impact)
  2. What Happened (Systemic Root Causes: client vs backend/transport factors)
  3. Event Timeline / Sequence (e.g. Mermaid sequence diagram tracing component interactions)
  4. Actionable Resolutions & Proposed Fixes
- **Hypothesis Formulation**: When diagnosing a failure, always formulate an evidence-grounded hypothesis before concluding your investigation.
- **Explaining Behavior**: A question about how a test, feature, or message flow works is not a failure diagnosis. Answer it directly, without hypotheses, verdicts, verification matrices, or RCA sections. Read the complete source of any test you explain, including every helper it calls, and describe what the device must send and receive in terms of UDMI config, state, and event fields confirmed against the schemas, citing the relevant specification under `docs/`.

For failure diagnosis, follow the mandatory 3-Phase Cognitive Diagnostic Cycle:
1. Phase 1: Evidence Harvesting (Use tools to inspect schemas, site models, extract timelines with timestamps, transaction IDs RC:..., cutoff thresholds).
2. Phase 2: Built-in Adversarial Self-Audit (evaluate Claim-by-Claim Verification Matrix with CONFIRMED, REFUTED, NOT ASSESSED, or UNVERIFIED ASSUMPTION, and invalidate rival hypotheses). REFUTED requires a positive observation that excludes the hypothesis; use NOT ASSESSED when nothing you found bears on it.
3. Phase 3: Verified Synthesis (emit concise report format with Root Cause, Evidence, Fix, and optional Visual Diagrams).

Visual Diagram Guidelines:
- **Graphviz (DOT) Diagrams** (` ```dot ` or ` ```graphviz `): Use for topology architectures, device/gateway relationships, site models, and failure causality graphs. The Mantis UI automatically renders these as interactive SVG cards.
- **Mermaid Diagrams** (` ```mermaid `): Use for sequence diagrams, protocol handshakes, and transaction timelines between Sequencer, Broker, and Devices.

You have access to domain tools to inspect the environment, execute tests, query runtime databases, inspect schemas, and triage failures. Use tools to gather empirical evidence before making claims.
"""

        genai_tools = get_genai_tools()
        if context_mgr:
            contents = context_mgr.get_llm_contents(prompt)
        else:
            contents = [types.Content(role="user", parts=[types.Part.from_text(text=prompt)])]

        if tier is None:
            tier = self.classify_intent_tier(prompt)
        model_name = self.config.get_model_for_tier(tier)

        thinking_cfg = None
        if self.config.thinking_level:
            thinking_cfg = types.ThinkingConfig(thinking_level=self.config.thinking_level)

        config = types.GenerateContentConfig(
            system_instruction=system_instruction,
            tools=genai_tools if genai_tools else None,
            temperature=0.2,
            thinking_config=thinking_cfg,
        )
        # Tool access is withheld on the scoping step so the Actor cannot skip it.
        scoping_config = types.GenerateContentConfig(
            system_instruction=system_instruction,
            temperature=0.2,
            thinking_config=thinking_cfg,
        )

        final_answer = ""

        # The Actor scopes the problem itself on its first step, with tools withheld. The
        # resulting plan becomes a self-authored model turn rather than an instruction
        # handed to it, which carries far more weight in later turns.
        run_scoping = enable_scoping if enable_scoping is not None else (tier == ModelTier.PRO)
        required_subsystems: List[str] = []
        competing_hypotheses: List[str] = []
        # Set when the scoping plan declares INFORMATIONAL_SCOPE. The Critic and the
        # Arbitrator then check an explanation for completeness and accuracy instead
        # of demanding a hypothesis audit the question never called for.
        informational = False
        # The resolution gate fires at most once, so a model that cannot comply
        # still terminates instead of looping against the same rejection.
        hypothesis_gate_applied = False
        # Same bound for the scoping gate: one rejection of a compound hypothesis,
        # then the plan stands as written whether or not the rewrite complied.
        scoping_gate_applied = False
        rescope_pending = False
        # Subsystems whose SOURCE was actually opened, tracked separately from those
        # merely searched. A grep tells you a symbol exists; it does not let you rule
        # on how that subsystem behaves.
        read_subsystems: set = set()
        hypothesis_subsystems: Dict[int, List[str]] = {}
        # Runtime-evidence tiers the log tool actually returned. A declaration of
        # LOCAL_FILE or CLOUD is only credible if one of these calls produced it.
        obtained_evidence_tiers: set = set()
        if run_scoping:
            contents.append(types.Content(role="user", parts=[types.Part.from_text(text=SCOPING_DIRECTIVE)]))

        # Subsystems the Actor has actually examined, derived from tool arguments.
        examined_subsystems: set = set()

        # Step 1: Actor Phase (ReAct exploration & tool execution)
        for step in range(max_steps):
            check_cancel(f"step {step + 1}")
            metrics.total_steps += 1
            metrics.api_calls_count += 1

            # The first step is the scoping turn and runs without tools. A plan that was
            # sent back for rewrite re-enters the same turn under the same conditions.
            is_scoping_step = run_scoping and (step == 0 or rescope_pending)
            rescope_pending = False
            step_config = scoping_config if is_scoping_step else config
            notify("phase", phase="SCOPING" if is_scoping_step else "ACTOR", step=step + 1)

            function_calls = []
            step_text = ""
            raw_model_parts = []

            if hasattr(client.models, "generate_content_stream") and stream_callback:
                try:
                    stream = self._call_api_with_retry(
                        client.models,
                        "generate_content_stream",
                        model=model_name,
                        contents=contents,
                        config=step_config,
                    )
                    for chunk in stream:
                        if getattr(chunk, "function_calls", None):
                            function_calls.extend(chunk.function_calls)
                        chunk_text = ""
                        if getattr(chunk, "candidates", None) and chunk.candidates:
                            content = chunk.candidates[0].content
                            if content and getattr(content, "parts", None):
                                for part in content.parts:
                                    raw_model_parts.append(part)
                                    if getattr(part, "text", None):
                                        chunk_text += part.text
                                    if getattr(part, "function_call", None) and not function_calls:
                                        function_calls.append(part.function_call)
                        if not chunk_text and getattr(chunk, "text", None):
                            chunk_text = chunk.text or ""
                        if chunk_text:
                            emit(chunk_text)
                            step_text += chunk_text
                        if getattr(chunk, "usage_metadata", None):
                            um = chunk.usage_metadata
                            metrics.prompt_tokens = getattr(um, "prompt_token_count", metrics.prompt_tokens) or 0
                            metrics.candidates_tokens = getattr(um, "candidates_token_count", metrics.candidates_tokens) or 0
                            metrics.total_tokens = getattr(um, "total_token_count", metrics.total_tokens) or 0
                except Exception as stream_err:
                    # Report why streaming failed. Swallowing this silently hides real
                    # request errors and makes the non-streaming retry look like the
                    # origin of any subsequent failure.
                    emit(f"\n[Mantis Warning] Streaming call failed ({type(stream_err).__name__}: "
                         f"{stream_err}); retrying without streaming.\n")
                    # The stream may have failed partway through, leaving fragments of an
                    # incomplete response behind. The retry below returns the full response,
                    # so discard the partial state instead of appending a second copy onto it.
                    raw_model_parts = []
                    function_calls = []
                    step_text = ""
                    response = self._call_api_with_retry(
                        client.models,
                        "generate_content",
                        model=model_name,
                        contents=contents,
                        config=step_config,
                    )
                    if getattr(response, "candidates", None) and response.candidates:
                        content = response.candidates[0].content
                        if content and getattr(content, "parts", None):
                            raw_model_parts.extend(content.parts)
                            for part in content.parts:
                                if getattr(part, "text", None):
                                    step_text += part.text
                                if getattr(part, "function_call", None) and not function_calls:
                                    function_calls.append(part.function_call)
                    if not function_calls and getattr(response, "function_calls", None):
                        function_calls.extend(response.function_calls)
                    if getattr(response, "usage_metadata", None):
                        um = response.usage_metadata
                        metrics.prompt_tokens += getattr(um, "prompt_token_count", 0) or 0
                        metrics.candidates_tokens += getattr(um, "candidates_token_count", 0) or 0
                        metrics.total_tokens += getattr(um, "total_token_count", 0) or 0
                    if step_text:
                        emit(step_text)
            else:
                response = self._call_api_with_retry(
                    client.models,
                    "generate_content",
                    model=model_name,
                    contents=contents,
                    config=step_config,
                )
                step_text = ""
                function_calls = getattr(response, "function_calls", None) or []
                if getattr(response, "candidates", None) and response.candidates:
                    content = response.candidates[0].content
                    if content and getattr(content, "parts", None):
                        raw_model_parts.extend(content.parts)
                        for part in content.parts:
                            if getattr(part, "text", None):
                                step_text += part.text
                            if getattr(part, "function_call", None) and not function_calls:
                                function_calls.append(part.function_call)
                if getattr(response, "usage_metadata", None):
                    um = response.usage_metadata
                    metrics.prompt_tokens += getattr(um, "prompt_token_count", 0) or 0
                    metrics.candidates_tokens += getattr(um, "candidates_token_count", 0) or 0
                    metrics.total_tokens += getattr(um, "total_token_count", 0) or 0
                if not step_text and not function_calls and getattr(response, "text", None):
                    step_text = response.text or ""
                if step_text and not function_calls:
                    emit(step_text)

            if is_scoping_step:
                plan_text = (step_text or "").strip()
                if not plan_text:
                    raise RuntimeError("Mantis Actor returned an empty scoping plan.")

                # Record the plan as the Actor's OWN turn, so it reads as a self-authored
                # commitment for the remainder of the investigation.
                if raw_model_parts:
                    contents.append(
                        types.Content(role="model", parts=_coalesce_model_parts(raw_model_parts))
                    )
                else:
                    contents.append(
                        types.Content(role="model", parts=[types.Part.from_text(text=plan_text)])
                    )

                required_subsystems, rejected = self._resolve_required_subsystems(plan_text)
                if rejected:
                    emit(
                        f"\n[Mantis Scoping] Ignoring subsystems absent from the repository: "
                        f"{', '.join(rejected)}\n"
                    )
                if not required_subsystems:
                    emit("\n[Mantis Scoping] No resolvable subsystems declared; coverage tracking disabled.\n")
                else:
                    emit(f"\n[Mantis Scoping] Contracted subsystems: {', '.join(required_subsystems)}\n")

                failure_scope = self._parse_failure_scope(plan_text)
                if failure_scope is None:
                    emit(
                        "\n[Mantis Scoping] Plan declared no recognized FAILURE_SCOPE "
                        f"({' | '.join(FAILURE_SCOPES)}); treating the request as a defect "
                        "report.\n"
                    )
                informational = failure_scope == INFORMATIONAL_SCOPE
                if informational:
                    # A plan that declared NOT_A_FAILURE yet listed hypotheses anyway is
                    # not held to them: there is no failure for them to explain, and
                    # tracking them would reinstate the audit this scope exists to skip.
                    if self._parse_competing_hypotheses(plan_text):
                        emit(
                            "\n[Mantis Scoping] Hypotheses listed for an informational "
                            "question were discarded.\n"
                        )
                    competing_hypotheses = []
                    emit(
                        "[Mantis Scoping] Informational question: answering directly, "
                        "without hypothesis ranking.\n"
                    )
                    contents.append(
                        types.Content(
                            role="user",
                            parts=[types.Part.from_text(text=INFORMATIONAL_DIRECTIVE)],
                        )
                    )
                    continue

                competing_hypotheses = self._parse_competing_hypotheses(plan_text)
                notify("hypotheses", hypotheses=list(competing_hypotheses))

                # A hypothesis that asserts two mechanisms cannot be ranked as a unit:
                # the better-evidenced half carries the other into its verdict, which is
                # how a well-evidenced client defect promoted an unexamined backend claim
                # to PRIMARY. Send the plan back once and require separate labels.
                compound = {
                    index: markers
                    for index, text in enumerate(competing_hypotheses, start=1)
                    if (markers := _compound_markers(text))
                }
                if compound and not scoping_gate_applied:
                    scoping_gate_applied = True
                    rescope_pending = True
                    detail = "; ".join(
                        "H{0} joins clauses with {1}".format(
                            index, ", ".join(repr(marker) for marker in markers)
                        )
                        for index, markers in sorted(compound.items())
                    )
                    emit(
                        f"\n[Mantis Scoping] Plan rejected: {detail}. "
                        f"Rewriting hypotheses as one mechanism each.\n"
                    )
                    contents.append(
                        types.Content(
                            role="user",
                            parts=[
                                types.Part.from_text(
                                    text=(
                                        "[Orchestrator Directive — Scoping Rejected]\n"
                                        f"{detail}.\n"
                                        "A hypothesis joining two mechanisms cannot be ranked: "
                                        "whichever half is better evidenced drags the other into "
                                        "the verdict with it, so the audit decides nothing.\n"
                                        "Re-emit the ENTIRE scoping structure, splitting each "
                                        "compound hypothesis into separate labels that each "
                                        "assert exactly one mechanism in one subsystem. Keep the "
                                        "mechanisms you believe in; give them their own labels "
                                        "and let the audit rank them independently. Tools remain "
                                        "withheld for this turn."
                                    )
                                )
                            ],
                        )
                    )
                    continue

                hypothesis_subsystems = self._hypothesis_subsystems(
                    competing_hypotheses, required_subsystems
                )
                if competing_hypotheses:
                    emit(
                        f"[Mantis Scoping] Tracking {len(competing_hypotheses)} hypotheses; "
                        f"each must be ranked ({', '.join(HYPOTHESIS_VERDICTS)}) with exactly "
                        f"one PRIMARY before the answer is accepted.\n"
                    )

                contents.append(
                    types.Content(
                        role="user",
                        parts=[
                            types.Part.from_text(
                                text=(
                                    "[Orchestrator Notice] Scoping accepted. Tool access is now restored. "
                                    "Begin the investigation and honor the plan you just committed to."
                                )
                            )
                        ],
                    )
                )
                continue

            if not function_calls:
                # Terminal step: Model emitted final answer. Before accepting it,
                # require a hypothesis audit that both accounts for every hypothesis
                # the Actor authored during scoping AND discriminates between them.
                # Two failure modes are guarded: silently abandoning the hypothesis
                # that happens to be correct, and endorsing all of them at once so
                # that nothing is actually excluded.
                violations = (
                    self._audit_violations(
                        step_text,
                        len(competing_hypotheses),
                        hypothesis_subsystems,
                        read_subsystems,
                        obtained_evidence_tiers,
                    )
                    if competing_hypotheses and not hypothesis_gate_applied
                    else []
                )
                if violations:
                    hypothesis_gate_applied = True
                    emit(
                        "\n[Mantis Gate] Answer rejected. "
                        + " ".join(violations)
                        + "\n"
                    )
                    self._append_model_turn(
                        contents,
                        types,
                        _coalesce_model_parts(raw_model_parts)
                        if raw_model_parts
                        else [types.Part.from_text(text=step_text)],
                        emit,
                    )
                    committed = "\n".join(
                        f"- H{i}: {text}"
                        for i, text in enumerate(competing_hypotheses, start=1)
                    )
                    contents.append(
                        types.Content(
                            role="user",
                            parts=[
                                types.Part.from_text(
                                    text=(
                                        "[Orchestrator Notice] Your answer is not accepted yet.\n"
                                        + "\n".join(f"- {v}" for v in violations)
                                        + "\n\nThese are the hypotheses you committed to during "
                                        f"scoping:\n{committed}\n\n"
                                        "You still have tool access. Investigate what you need, then "
                                        "reissue your COMPLETE final answer ending with a trailing "
                                        "'## Hypothesis Resolution Audit' section that assigns each "
                                        f"hypothesis exactly one of: {', '.join(HYPOTHESIS_VERDICTS)}. "
                                        "Exactly one may be PRIMARY. Cite evidence that supports that "
                                        "specific hypothesis: evidence that another hypothesis is true "
                                        "does not confirm this one."
                                    )
                                )
                            ],
                        )
                    )
                    continue

                final_answer = step_text
                break

            # Append model turn with accumulated function calls and text, preserving thought_signature
            if raw_model_parts:
                self._append_model_turn(
                    contents, types, _coalesce_model_parts(raw_model_parts), emit
                )
            else:
                model_parts = []
                if step_text:
                    model_parts.append(types.Part.from_text(text=step_text))
                for fc in function_calls:
                    model_parts.append(types.Part.from_function_call(name=fc.name, args=fc.args or {}))
                self._append_model_turn(contents, types, model_parts, emit)

            # Execute tool calls
            response_parts = []
            for call in function_calls:
                check_cancel(f"tool call {call.name}")
                call_name = call.name
                call_args = dict(call.args) if call.args else {}
                metrics.tool_calls[call_name] = metrics.tool_calls.get(call_name, 0) + 1

                # Record which repository subsystems this call actually touched, so the
                # orchestrator can tell the Actor what it has not yet looked at.
                for arg_key in ("path_prefix", "file_path"):
                    arg_val = call_args.get(arg_key)
                    if isinstance(arg_val, str) and arg_val.strip():
                        top_dir = arg_val.strip().lstrip("/").split("/")[0]
                        examined_subsystems.add(top_dir)
                        if call_name == "read_udmi_file":
                            read_subsystems.add(top_dir)

                call_id = f"{step + 1}-{call_name}-{metrics.tool_calls[call_name]}"
                emit(f"\n[Mantis ReAct Step {step+1}] Calling `{call_name}` with {json.dumps(call_args)}\n")
                notify(
                    "tool_call",
                    call_id=call_id,
                    tool=call_name,
                    args=call_args,
                    step=step + 1,
                )

                if call_name == "ensure_test_setup":
                    emit("  -> Provisioning local UDMI stack (starting Mosquitto, etcd, InfluxDB, PostgreSQL, UDMIS)...\n")
                elif call_name == "run_sequencer_test":
                    emit(f"  -> Launching sequencer test '{call_args.get('test_name')}' for {call_args.get('device_id', 'unknown_device')} against {call_args.get('target_spec', 'local')}...\n")
                elif call_name == "start_session_process":
                    emit(f"  -> Launching process in window '{call_args.get('window', 'sequencer')}'...\n")

                try:
                    tool_output = execute_tool(
                        name=call_name,
                        args=call_args,
                        session_mgr=self.session_mgr,
                        udmi_root=self.udmi_root,
                    )
                except Exception as err:
                    tool_output = {"status": "ERROR", "error": str(err)}

                tool_executions.append({"tool": call_name, "args": call_args, "output": tool_output})
                notify(
                    "tool_result",
                    call_id=call_id,
                    tool=call_name,
                    status=(
                        tool_output.get("status", "SUCCESS")
                        if isinstance(tool_output, dict)
                        else "SUCCESS"
                    ),
                    output=tool_output,
                )

                # Record the runtime-evidence tier actually delivered, so a later
                # claim of log-backed evidence can be checked against what was
                # retrieved instead of being taken at face value.
                if (
                    call_name == "get_udmis_runtime_logs"
                    and isinstance(tool_output, dict)
                    and tool_output.get("status") == "SUCCESS"
                    and tool_output.get("tier")
                ):
                    obtained_evidence_tiers.add(tool_output["tier"])

                if isinstance(tool_output, dict):
                    if tool_output.get("status") == "READY":
                        emit(f"  -> Environment '{tool_output.get('test_id')}' is READY at {tool_output.get('connection_url')}\n")
                        self.active_session_id = tool_output.get("test_id")
                        if context_mgr:
                            context_mgr.context.active_session_id = tool_output.get("test_id")
                            context_mgr.save_context()
                    elif tool_output.get("status") == "LAUNCHED":
                        emit(f"  -> Sequencer test '{tool_output.get('test_name')}' LAUNCHED against {tool_output.get('target_spec')} in session '{tool_output.get('session_id')}'\n")
                        self.active_session_id = tool_output.get("session_id")
                        if context_mgr:
                            context_mgr.context.active_session_id = tool_output.get("session_id")
                            context_mgr.context.active_test_id = tool_output.get("test_name")
                            context_mgr.context.active_device_id = tool_output.get("device_id")
                            context_mgr.save_context()
                    elif tool_output.get("status") == "ERROR":
                        emit(f"  -> Tool execution error: {tool_output.get('error')}\n")

                resp_payload = tool_output if isinstance(tool_output, dict) else {"result": tool_output}
                # Must happen before serialisation: Vertex rejects the whole
                # request if a `$ref` key survives anywhere in the response.
                resp_payload = _neutralize_schema_refs(resp_payload)
                max_payload_chars = 30000
                try:
                    payload_str = json.dumps(resp_payload, default=str)
                except (TypeError, ValueError) as serialize_error:
                    # Report the failure to the model rather than swallowing it.
                    # Passing the original object through instead would smuggle an
                    # un-neutralised `$ref` into the request and kill the entire
                    # investigation with a 400, far from the tool that caused it.
                    logger.warning(
                        "tool %s returned an unserialisable payload: %s",
                        call_name, serialize_error,
                    )
                    resp_payload = {
                        "status": "ERROR",
                        "error": f"Tool output could not be serialised: {serialize_error}",
                    }
                else:
                    if len(payload_str) > max_payload_chars:
                        # The snippet is a *string*, so any `$ref` inside it is
                        # inert: the reserved-key scan only looks at real JSON
                        # keys, which live probing confirmed.
                        resp_payload = {
                            "status": resp_payload.get("status", "SUCCESS") if isinstance(resp_payload, dict) else "SUCCESS",
                            "truncated": True,
                            "warning": f"Tool output exceeded {max_payload_chars} characters and was truncated by MantisAgent safety cap.",
                            "truncated_snippet": payload_str[:max_payload_chars],
                        }

                response_parts.append(
                    types.Part.from_function_response(
                        name=call_name,
                        response=resp_payload,
                    )
                )

            # Step Budget Awareness: inform the model how much exploration budget remains
            # so it can converge on an answer instead of being cut off mid-investigation.
            # Also surface any contracted subsystem it has not yet touched, which is the
            # dominant cause of prematurely narrow diagnoses.
            #
            # These notices are plain text and must NOT be mixed into the function-response
            # turn: a turn carrying function_response parts may contain nothing else, and
            # violating that gets the whole turn rejected, which makes the request look as
            # though it ends on the preceding model turn.
            notices = []
            steps_remaining = max_steps - (step + 1)
            # Coverage is measured by files actually READ, not by subsystems touched.
            # A grep reports that a symbol exists; it does not show what the code does.
            # Crediting a search as examination is what previously let the coverage
            # notice fall silent while the Actor had opened nothing in the subsystem.
            unread = [s for s in required_subsystems if s not in read_subsystems]
            if unread:
                searched_only = [s for s in unread if s in examined_subsystems]
                untouched = [s for s in unread if s not in examined_subsystems]
                detail = []
                if searched_only:
                    detail.append(
                        f"you have searched {', '.join(searched_only)} but have not opened "
                        f"any file there"
                    )
                if untouched:
                    detail.append(f"you have not touched {', '.join(untouched)} at all")
                notices.append(
                    f"[Orchestrator Notice] Contract coverage gap: {'; '.join(detail)}. "
                    f"Your scoping phase declared these subsystems as required. Searching is "
                    f"not examining: search_codebase results do not count as coverage. Read the "
                    f"relevant implementation files with read_udmi_file, or explicitly justify "
                    f"why they are architecturally irrelevant."
                )
            if steps_remaining <= BUDGET_WARNING_THRESHOLD:
                notices.append(
                    f"[Orchestrator Notice] Exploration budget: {steps_remaining} tool step(s) remaining "
                    f"out of {max_steps}. Prioritize converging on a conclusion. "
                    f"When the budget reaches 0 you will be required to answer from the evidence already gathered, "
                    f"with no further tool access."
                )

            contents.append(types.Content(role="user", parts=response_parts))
            if notices:
                contents.append(
                    types.Content(
                        role="user",
                        parts=[types.Part.from_text(text="\n".join(notices))],
                    )
                )

        if not final_answer:
            check_cancel("forced synthesis")
            # Budget exhausted while the model was still calling tools. Force a terminal
            # synthesis turn with tools disabled so the model must produce a real answer
            # grounded in the evidence it already collected.
            emit(
                f"\n[Mantis Actor] Exploration budget ({max_steps} steps) exhausted. "
                f"Forcing synthesis from collected evidence...\n"
            )
            synthesis_config = types.GenerateContentConfig(
                system_instruction=system_instruction,
                temperature=0.2,
            )
            # The resolution requirement must survive budget exhaustion. Without this
            # the Actor can burn its whole budget and then answer through a path that
            # never has to account for the hypotheses it committed to.
            synthesis_directive = (
                "[Orchestrator Directive] Your exploration budget is exhausted and tool access is "
                "now revoked. You MUST now answer the user's original inquiry directly and completely, "
                "using only the evidence already gathered above. Do not request further tools. "
                "State clearly which conclusions are firmly grounded in the evidence and which remain "
                "unverified hypotheses requiring follow-up."
            )
            if competing_hypotheses:
                committed = "\n".join(
                    f"- H{i}: {text}" for i, text in enumerate(competing_hypotheses, start=1)
                )
                synthesis_directive += (
                    "\n\nYour answer MUST end with a trailing '## Hypothesis Resolution Audit' section "
                    f"resolving every hypothesis you committed to during scoping:\n{committed}\n"
                    f"Assign each one exactly one of: {', '.join(HYPOTHESIS_VERDICTS)}. "
                    "EXACTLY ONE may be PRIMARY. Cite the evidence behind each verdict, and cite "
                    "evidence that supports that specific hypothesis rather than a different one. "
                    "If your evidence cannot decide, mark it UNRESOLVED and state exactly what "
                    "evidence would settle it. Do not omit a hypothesis."
                )
                never_read = sorted(
                    {
                        subsystem
                        for subsystems in hypothesis_subsystems.values()
                        for subsystem in subsystems
                        if subsystem not in read_subsystems
                    }
                )
                if never_read:
                    # Tools are already revoked here, so demanding a decisive verdict on
                    # unexamined code would only invite an invented one.
                    synthesis_directive += (
                        f"\n\nYou never read any source in: {', '.join(never_read)}. Any "
                        "hypothesis about those subsystems must be marked UNRESOLVED. Do not "
                        "assert how code you did not read behaves."
                    )
                backend_named = sorted(
                    {
                        subsystem
                        for subsystems in hypothesis_subsystems.values()
                        for subsystem in subsystems
                        if subsystem in BACKEND_SUBSYSTEMS
                    }
                )
                if backend_named:
                    if obtained_evidence_tiers:
                        obtained_clause = (
                            f"During this investigation get_udmis_runtime_logs returned only: "
                            f"{', '.join(sorted(obtained_evidence_tiers))}. No other tier may be "
                            f"declared."
                        )
                    else:
                        obtained_clause = (
                            "You obtained no runtime logs during this investigation, so the only "
                            f"declaration available is 'RUNTIME_EVIDENCE: NONE "
                            f"({RUNTIME_EVIDENCE_NONE_QUALIFIER})'."
                        )
                    synthesis_directive += (
                        f"\n\nHypotheses about {', '.join(backend_named)} concern runtime "
                        "behavior, so each must carry its own line stating the basis of its "
                        f"verdict: 'RUNTIME_EVIDENCE: {' | '.join(RUNTIME_EVIDENCE_TIERS)}'. "
                        f"{obtained_clause} Declaring NONE is an acceptable basis for a verdict, "
                        "but it must be stated rather than implied. Do not describe source "
                        "analysis as though it were an observation of runtime behavior."
                    )
            contents.append(
                types.Content(
                    role="user",
                    parts=[types.Part.from_text(text=synthesis_directive)],
                )
            )
            metrics.api_calls_count += 1
            synthesis_resp = self._call_api_with_retry(
                client.models,
                "generate_content",
                model=model_name,
                contents=contents,
                config=synthesis_config,
            )
            final_answer = getattr(synthesis_resp, "text", "") or ""
            if getattr(synthesis_resp, "usage_metadata", None):
                um = synthesis_resp.usage_metadata
                metrics.prompt_tokens += getattr(um, "prompt_token_count", 0) or 0
                metrics.candidates_tokens += getattr(um, "candidates_token_count", 0) or 0
                metrics.total_tokens += getattr(um, "total_token_count", 0) or 0
            if not final_answer.strip():
                raise RuntimeError(
                    f"Mantis Actor failed to produce an answer after {max_steps} exploration steps "
                    f"and a forced synthesis turn. Collected {len(tool_executions)} tool execution(s)."
                )
            emit(final_answer)

        # ----------------------------------------------------------------------
        # Tripartite Loop: Critic -> Arbitrator
        # ----------------------------------------------------------------------
        actor_answer = final_answer
        run_tripartite = False
        if enable_tripartite is True:
            run_tripartite = True
        elif enable_tripartite is None and tier == ModelTier.PRO:
            if hasattr(client, "models") and hasattr(client.models, "responses"):
                if len(client.models.responses) >= 2:
                    run_tripartite = True
            elif self.client is not None:
                run_tripartite = False
            else:
                run_tripartite = True

        if run_tripartite:
            check_cancel("the Critic phase")
            notify("phase", phase="CRITIC")
            emit("\n[Mantis Critic] Initiating adversarial audit against UDMI codebase & schemas...\n")
            pro_model_name = self.config.get_model_for_tier(ModelTier.PRO)

            # 1. Critic Step
            # An explanation is audited for completeness, which the Critic cannot judge
            # from 600-character fragments of the source it is checking against.
            snippet_limit = 6000 if informational else 600

            def _format_snippet(tool: str, output_obj: Any) -> str:
                s = str(output_obj)
                if informational and tool in SOURCE_EVIDENCE_TOOLS:
                    return s
                if len(s) > snippet_limit:
                    return s[:snippet_limit] + f" ...[truncated {len(s) - snippet_limit} chars]"
                return s

            critic_summary = json.dumps(
                [{"tool": e["tool"], "args": e["args"], "output_snippet": _format_snippet(e["tool"], e["output"])} for e in tool_executions],
                indent=2,
            )
            if informational:
                critic_instruction = (
                    "You are the Mantis Critic. The user asked an informational question, not a "
                    "defect report, and the Actor has written an explanation.\n"
                    "The UDMI codebase, schemas, and specifications are the Single Source of Truth (SSoT).\n"
                    "Audit the explanation against the tool evidence for:\n"
                    "1. Accuracy: every field path, constant, timing value, and behavior it states "
                    "must appear in the evidence. Flag any field or mechanism the evidence does not "
                    "show, including plausible-sounding field names that do not exist in the schema.\n"
                    "2. Completeness: compare the explanation with the source the Actor read. Name "
                    "every check, wait, precondition, or step present in the source that the "
                    "explanation omits, and any source the Actor did not finish reading.\n"
                    "3. Framing: expected device behavior must be stated as UDMI config, state, and "
                    "event fields, with the specification kept distinct from how the sequencer checks it.\n"
                    "Do not ask for hypotheses, verdicts, or runtime-evidence declarations; they do "
                    "not apply to an explanation.\n"
                    "Source reads (read_udmi_file, inspect_sequencer_test, inspect_udmi_schema, "
                    "locate_udmi_doc) are shown to you exactly as the Actor received them. Other "
                    "outputs may end in '...[truncated N chars]': the Actor saw those in full, so "
                    "a claim that could rest on the cut portion is not unsupported; list it under "
                    "'Not verifiable from the excerpt'. A read_udmi_file result whose own "
                    "'truncated' field is true did stop at 'end_line'; content past that line "
                    "was not read.\n"
                    "Provide your audit in this format:\n"
                    "- Verified Claims: ...\n"
                    "- Unsupported or Incorrect Claims: ...\n"
                    "- Not verifiable from the excerpt: ...\n"
                    "- Omitted Steps or Checks: ...\n"
                    "- Recommendation for Arbitrator: ..."
                )
            else:
                critic_instruction = (
                    "You are the Mantis Critic. Your role is an adversarial, hyper-rigorous audit of the Actor's findings.\n"
                    "The UDMI codebase, schemas, and specifications are the Single Source of Truth (SSoT).\n"
                    "Audit the Actor's response against the tool evidence and UDMI specifications.\n"
                    "If the Actor produced a 'Hypothesis Resolution Audit', scrutinize each verdict "
                    "against the evidence cited FOR THAT HYPOTHESIS. A verdict is unsound when the "
                    "evidence actually supports a different hypothesis, when a hypothesis is ranked "
                    "PRIMARY on evidence that does not establish causation, or when a hypothesis is "
                    "marked REFUTED without evidence that excludes it. Report every such mismatch.\n"
                    "Backend hypotheses carry a 'RUNTIME_EVIDENCE:' declaration. Check it against "
                    "the tool evidence: a hypothesis declaring LOCAL_FILE or CLOUD must cite actual "
                    "log content, and one declaring NONE must not be narrated as though the behavior "
                    "was observed. A sound conclusion read from source is acceptable; describing an "
                    "inference as an observation is not.\n"
                    "Provide your critical audit in this format:\n"
                    "- Verified Claims: ...\n"
                    "- Unverified / Refuted Claims: ...\n"
                    "- Unsound Hypothesis Verdicts: ...\n"
                    "- Missing Context / Omissions: ...\n"
                    "- Recommendation for Arbitrator: ..."
                )
            critic_user = (
                f"User Inquiry: {prompt}\n\n"
                f"Actor's Proposed Response:\n{actor_answer}\n\n"
                f"Empirical Tool Evidence Collected by Actor:\n{critic_summary}"
            )
            critic_config = types.GenerateContentConfig(
                system_instruction=critic_instruction,
                temperature=0.1,
            )
            critic_contents = [types.Content(role="user", parts=[types.Part.from_text(text=critic_user)])]

            try:
                metrics.api_calls_count += 1
                critic_resp = self._call_api_with_retry(
                    client.models,
                    "generate_content",
                    model=pro_model_name,
                    contents=critic_contents,
                    config=critic_config,
                )
                critic_text = getattr(critic_resp, "text", "") or ""
                emit(f"\n[Mantis Critic Audit]\n{critic_text}\n")
            except Exception as e:
                logger.error(f"Critic audit failed: {e}")
                critic_text = f"Critic audit skipped due to execution error: {e}"
                metrics.tripartite_degraded = True
                metrics.tripartite_status = "CRITIC_FAILED"

            # 2. Arbitrator Step
            check_cancel("the Arbitrator phase")
            notify("phase", phase="ARBITRATOR")
            emit("\n[Mantis Arbitrator] Synthesizing verified final response...\n")
            arbitrator_instruction = (
                "You are the Mantis Arbitrator, the final authoritative voice of Mantis.\n"
                "You evaluate the Actor's findings and the Critic's adversarial audit to produce the final, definitive response.\n"
                "Tasks:\n"
                "1. Reconcile any discrepancies between Actor and Critic.\n"
                "2. Eliminate any ungrounded assumptions or incorrect claims.\n"
                "3. Synthesize the final, verified, polished response to the user, keeping the \n"
                "   structure and audience the User Inquiry asks for.\n"
                "4. Preserve the Actor's 'Hypothesis Resolution Audit' as the LAST section of your\n"
                "   response, under exactly the heading '## Hypothesis Resolution Audit', with each\n"
                "   hypothesis's Verdict, RUNTIME_EVIDENCE (where present), and Rationale lines.\n"
                "   Keep every hypothesis, its verdict, its 'RUNTIME_EVIDENCE:' declaration, and the\n"
                "   evidence cited. You may correct a verdict the Critic showed to be wrong, but you\n"
                "   may not drop the section, omit a hypothesis, or remove a declaration: together\n"
                "   they are the record of what was ruled out, why, and on what basis.\n"
                "5. Autonomous Visualization: Decide if a diagram would substantially improve clarity:\n"
                "   - If explaining network topology, architecture, or gateway-device relationships: Include a Graphviz DOT diagram (```dot ... ```) and/or Mermaid (```mermaid ... ```).\n"
                "   - If explaining message sequences, state transitions, or transaction timelines: Include a Mermaid sequenceDiagram (```mermaid ... ```) and/or Graphviz DOT (```dot ... ```).\n"
                "   - If a diagram is not helpful, do not include one."
            )
            arbitrator_closing = (
                "Produce the definitive, verified response, retaining the Hypothesis "
                "Resolution Audit section."
            )
            if informational:
                arbitrator_instruction = (
                    "You are the Mantis Arbitrator, the final authoritative voice of Mantis.\n"
                    "The user asked an informational question, not a defect report. You receive the "
                    "Actor's explanation and the Critic's audit of it.\n"
                    "Tasks:\n"
                    "1. Remove every claim the Critic showed to be unsupported or incorrect. Do not "
                    "replace it with a guess; if the evidence does not settle a point, say so plainly. "
                    "Keep claims the Critic listed only as 'Not verifiable from the excerpt'.\n"
                    "2. Add every step or check the Critic showed was omitted, using only what the "
                    "Critic quoted from the evidence.\n"
                    "3. Write the answer for a reader who implements UDMI devices from the "
                    "specification and may never have seen the UDMI codebase. Keep this structure:\n"
                    "   Summary; Step by step (with the check made at each step); What the device "
                    "must do (config received, state reported, events published, with field paths "
                    "and timing); References (spec documents, schemas, and source files by path).\n"
                    "4. Do not include hypotheses, verdicts, a Hypothesis Resolution Audit, "
                    "RUNTIME_EVIDENCE declarations, or commentary about the Actor or Critic. The "
                    "reader sees only the answer.\n"
                    "5. Include a Mermaid diagram (```mermaid ... ```) only if it clarifies a message "
                    "sequence or interaction."
                )
                arbitrator_closing = "Produce the definitive, verified explanation."
            arbitrator_user = (
                f"User Inquiry: {prompt}\n\n"
                f"Actor's Response:\n{actor_answer}\n\n"
                f"Critic's Audit:\n{critic_text}\n\n"
                f"{arbitrator_closing}"
            )
            arbitrator_config = types.GenerateContentConfig(
                system_instruction=arbitrator_instruction,
                temperature=0.2,
            )
            arbitrator_contents = [types.Content(role="user", parts=[types.Part.from_text(text=arbitrator_user)])]

            try:
                metrics.api_calls_count += 1
                arbitrator_resp = self._call_api_with_retry(
                    client.models,
                    "generate_content",
                    model=pro_model_name,
                    contents=arbitrator_contents,
                    config=arbitrator_config,
                )
                arbitrator_text = getattr(arbitrator_resp, "text", "") or ""
                if competing_hypotheses and arbitrator_text:
                    arb_violations = self._audit_violations(
                        answer_text=arbitrator_text,
                        hypothesis_count=len(competing_hypotheses),
                        hypothesis_subsystems=hypothesis_subsystems,
                        read_subsystems=read_subsystems,
                        obtained_evidence_tiers=obtained_evidence_tiers,
                    )
                    if arb_violations:
                        logger.warning(
                            "Arbitrator output violated hypothesis audit rules: %s. Falling back to verified Actor answer.",
                            arb_violations,
                        )
                        emit(
                            f"\n[Mantis Arbitrator Warning: Output failed audit gates ({'; '.join(arb_violations)}); falling back to Actor answer]\n"
                        )
                        metrics.tripartite_degraded = True
                        metrics.tripartite_status = "ARBITRATOR_GATE_VIOLATION"
                        final_answer = actor_answer
                    else:
                        final_answer = arbitrator_text
                else:
                    final_answer = arbitrator_text or actor_answer
            except Exception as e:
                logger.error(f"Arbitrator failed to synthesize final answer: {e}")
                emit(f"\n[Mantis Arbitrator Error: {e}; falling back to Actor answer]\n")
                metrics.tripartite_degraded = True
                metrics.tripartite_status = "ARBITRATOR_FAILED"
                final_answer = actor_answer

        metrics.total_duration_sec = round(time.time() - start_time, 3)
        if context_mgr:
            context_mgr.context.metrics = metrics
            context_mgr.save_context()

        # The audit is reported from the answer that is actually being returned, so a
        # consumer never renders verdicts belonging to a draft that was discarded at
        # an arbitrator gate. A hypothesis the answer left unranked is reported with a
        # null verdict rather than being dropped or defaulted to a verdict nobody gave.
        if competing_hypotheses:
            resolved = self._hypothesis_verdicts(final_answer, len(competing_hypotheses))
            # Rationale and runtime-evidence tier are read from the trailing audit
            # section only: outside it, text about a hypothesis is report prose, not
            # the reasoning recorded for its verdict. No section means no rationale.
            _, audit_section = split_audit_section(final_answer)
            entries = parse_audit_entries(audit_section or "", len(competing_hypotheses))
            notify(
                "audit",
                hypotheses=[
                    {
                        "label": f"H{index}",
                        "hypothesis": text,
                        "verdict": resolved.get(index),
                        "rationale": entries[index]["rationale"],
                        "evidence_tier": entries[index]["evidence_tier"],
                    }
                    for index, text in enumerate(competing_hypotheses, start=1)
                ],
                audit_section=audit_section,
            )

        notify(
            "metrics",
            duration_sec=metrics.total_duration_sec,
            steps=metrics.total_steps,
            tool_calls=dict(metrics.tool_calls),
            tripartite_status=getattr(metrics, "tripartite_status", None),
        )

        return final_answer

    def run_tripartite(
        self,
        prompt: str,
        context: Optional[SessionContext] = None,
        stream_callback: Optional[Callable[[str], None]] = None,
    ) -> Dict[str, Any]:
        """Explicitly executes the full Actor -> Critic -> Arbitrator tripartite loop and returns all stage outputs."""
        if context is not None:
            ctx_mgr = ContextManager(context=context, udmi_root=self.udmi_root)
        else:
            ctx_mgr = ContextManager(udmi_root=self.udmi_root)

        final_text = self._run_llm(
            prompt=prompt,
            context_mgr=ctx_mgr,
            stream_callback=stream_callback,
            tier=ModelTier.PRO,
            enable_tripartite=True,
        )
        status = "DEGRADED" if (ctx_mgr.context.metrics and getattr(ctx_mgr.context.metrics, "tripartite_degraded", False)) else "SUCCESS"
        return {
            "status": status,
            "prompt": prompt,
            "final_answer": final_text,
            "metrics": ctx_mgr.context.metrics,
        }

    # --------------------------------------------------------------------------
    # Helper Utilities
    # --------------------------------------------------------------------------

    def _handle_bundle_triage(self, bundle_file: str) -> str:
        """Ingests a support bundle and performs root-cause analysis."""
        ingest_res = ingest_support_bundle(bundle_file, udmi_root=self.udmi_root)
        if ingest_res.get("status") != "SUCCESS":
            return f"Error ingesting bundle: {ingest_res.get('error')}"

        manifest = ingest_res.get("manifest", {})
        site = manifest.get("site_name", "sites/udmi_site_model")
        device = manifest.get("device_id") or "UNKNOWN_DEVICE"
        test = manifest.get("failed_test") or "UNKNOWN_TEST"

        diag = self.diagnose_test_failure(
            test_id=test,
            device_id=device,
            site_model=site,
            run_dir=ingest_res.get("extracted_dir"),
        )
        return f"Support Bundle Ingestion: `{bundle_file}`\n\n" + diag.get("report", "")

    def _extract_word(self, text: str, pattern: str, default: Optional[str] = None) -> Any:
        m = re.search(pattern, text, re.IGNORECASE)
        if m:
            val = (m.group(1) or (m.group(2) if len(m.groups()) >= 2 else None) or "").strip()
            if val and val.lower() not in (
                "for", "with", "on", "at", "to", "in", "the", "a", "an", "of", "and", "is",
                "by", "from", "me", "us", "it", "them", "him", "her", "localhost", "cloud",
                "broker", "server", "monday", "tuesday", "wednesday", "thursday", "friday",
                "saturday", "sunday", "today", "yesterday", "tomorrow"
            ):
                return val
        return default

    def _parse_patch_data(self, query: str) -> Dict[str, Any]:
        """Robustly parses configuration patch fields from user instructions."""
        # 1. Direct JSON payload in query
        first_brace = query.find("{")
        last_brace = query.rfind("}")
        if first_brace != -1 and last_brace > first_brace:
            try:
                data = json.loads(query[first_brace : last_brace + 1])
                if isinstance(data, dict):
                    return data
            except Exception:
                pass

        patch_data: Dict[str, Any] = {}

        # 2. nostate flag
        if "nostate" in query.lower():
            patch_data.setdefault("testing", {})["nostate"] = True

        # 3. Known shortcuts
        sr_match = re.search(r"sample_rate_sec\s*(?:to|=)?\s*([0-9]+)", query, re.IGNORECASE)
        if sr_match:
            patch_data.setdefault("pointset", {})["sample_rate_sec"] = int(sr_match.group(1))

        proxy_match = re.search(r"(?:proxy_id|gateway_id)\s*(?:to|=)?\s*['\"]?([A-Za-z0-9_-]+)['\"]?", query, re.IGNORECASE)
        if proxy_match:
            patch_data.setdefault("gateway", {})["gateway_id"] = proxy_match.group(1)

        min_log_match = re.search(r"(?:min_loglevel|min_log_level|loglevel)\s*(?:to|=)?\s*([0-9]+)", query, re.IGNORECASE)
        if min_log_match:
            patch_data.setdefault("system", {})["min_loglevel"] = int(min_log_match.group(1))

        # 4. General dot-notation or key-value assignments (e.g. "set system.min_loglevel to 200")
        kv_matches = re.finditer(r"(?:set|patch|update)?\s*([a-zA-Z0-9_\.]+)\s*(?:to|=)\s*['\"]?([^'\"\s,;]+)['\"]?", query, re.IGNORECASE)
        for m in kv_matches:
            key_path = m.group(1).strip()
            raw_val = m.group(2).strip()
            if key_path.lower() in ("device", "for", "in", "site", "site_model"):
                continue

            val: Any = raw_val
            if raw_val.lower() == "true":
                val = True
            elif raw_val.lower() == "false":
                val = False
            elif raw_val.isdigit():
                val = int(raw_val)
            else:
                try:
                    val = float(raw_val)
                except ValueError:
                    val = raw_val

            # Build nested dict from dot-path
            parts = key_path.split(".")
            curr = patch_data
            for p in parts[:-1]:
                curr = curr.setdefault(p, {})
            curr[parts[-1]] = val

        return patch_data
