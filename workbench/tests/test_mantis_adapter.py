"""Unit tests for the Mantis SSE adapter's triage prompt and audit translation."""

import json
import os

import pytest

from mantis.config import ProviderType
from workbench.server import mantis_adapter
from workbench.server.discovery import DiscoveryError
from workbench.server.mantis_adapter import MantisSessionStore

DEVICE = "AHU-1"
TEST = "endpoint_connection_success"


class _StubSessionManager:
    def __init__(self, udmi_root):
        self.udmi_root = udmi_root


def _site_model(tmp_path, with_run):
    site = tmp_path / "lab_site"
    (site / "devices" / DEVICE).mkdir(parents=True)
    (site / "cloud_iot_config.json").write_text(json.dumps({"site_name": "LAB"}), encoding="utf-8")
    run_dir = site / "out" / "devices" / DEVICE / "tests" / TEST
    if with_run:
        run_dir.mkdir(parents=True)
    return str(site), str(run_dir)


def _store_with_context(tmp_path, site_model):
    store = MantisSessionStore(_StubSessionManager(str(tmp_path)))
    context = store.get_or_create("sess")
    context.active_site_model = site_model
    context.active_device_id = DEVICE
    context.active_test_id = TEST
    return store, context


def test_triage_prompt_passes_the_recorded_run_dir_to_diagnose_test_failure(tmp_path):
    """Without run_dir, diagnose_test_failure searched repo-wide locations and could
    read another run's logs. The run directory the adapter found must be handed to it."""
    site, run_dir = _site_model(tmp_path, with_run=True)
    store, context = _store_with_context(tmp_path, site)

    prompt = store._triage_prompt(context)

    expected_call = (
        f"diagnose_test_failure(test_id='{TEST}', device_id='{DEVICE}', "
        f"site_model='{site}', run_dir='{os.path.abspath(run_dir)}')"
    )
    assert expected_call in prompt
    assert os.path.isabs(run_dir)


def test_triage_prompt_without_a_recorded_run_forbids_timeline_tools(tmp_path):
    """With no recorded run, telling the agent to call diagnose_test_failure made it
    harvest whatever lay under the repository's out/. Step 1 must state there is no
    recorded run and forbid the log and timeline tools."""
    site, run_dir = _site_model(tmp_path, with_run=False)
    store, context = _store_with_context(tmp_path, site)

    prompt = store._triage_prompt(context)

    assert "1. There is no recorded run for this test" in prompt
    assert "Do NOT call diagnose_test_failure" in prompt
    assert "Call diagnose_test_failure(" not in prompt
    assert "run_dir=" not in prompt


def test_run_dir_surfaces_site_model_resolution_errors(tmp_path):
    """An unresolvable site model is a configuration fault, not 'no artifacts'."""
    store, context = _store_with_context(tmp_path, str(tmp_path / "missing_site"))
    with pytest.raises(DiscoveryError):
        store._run_dir(context)


def test_triage_stream_reports_unresolvable_site_model_as_error(tmp_path, monkeypatch):
    monkeypatch.setattr(mantis_adapter.CONFIG, "provider_override", ProviderType.AI_STUDIO)
    missing = str(tmp_path / "missing_site")
    store = MantisSessionStore(_StubSessionManager(str(tmp_path)))

    events = list(store.stream_chat_events(
        {
            "session_id": "sess-missing",
            "message": f"Diagnose failure for test {TEST}",
            "context": {"site_model": missing, "device_id": DEVICE, "test_id": TEST},
        },
        correlation_id="corr",
    ))

    assert [e["event"] for e in events] == ["error"]
    message = events[0]["data"]["message"]
    assert missing in message
    assert "did not resolve" in message


def test_audit_translation_does_not_fabricate_evidence_or_rationale(tmp_path):
    """The agent's audit record carries hypothesis and verdict only. The adapter
    previously stamped LOCAL_FILE evidence on every row with a verdict, claiming log
    evidence that may never have been read."""
    store = MantisSessionStore(_StubSessionManager(str(tmp_path)))
    events = store._translate(
        {
            "type": "audit",
            "hypotheses": [
                {"label": "H1", "hypothesis": "The device is offline.", "verdict": "REFUTED"},
                {"label": "H2", "hypothesis": "Config is stale.", "verdict": "PRIMARY"},
                {"label": "H3", "hypothesis": "Clock skew.", "verdict": None},
            ],
        },
        [],
        {},
    )

    rows = events[0]["data"]["hypotheses"]
    assert [r["verdict"] for r in rows] == ["REFUTED", "PRIMARY", "UNRESOLVED"]
    assert [r["evidence_tier"] for r in rows] == [None, None, None]
    assert [r["rationale"] for r in rows] == [None, None, None]


def test_audit_translation_forwards_agent_supplied_evidence_and_rationale(tmp_path):
    store = MantisSessionStore(_StubSessionManager(str(tmp_path)))
    events = store._translate(
        {
            "type": "audit",
            "hypotheses": [{
                "hypothesis": "Config is stale.",
                "verdict": "PRIMARY",
                "rationale": "sequence.log shows no configAcked.",
                "evidence_tier": "LOCAL_FILE",
            }],
        },
        [],
        {},
    )
    row = events[0]["data"]["hypotheses"][0]
    assert row["rationale"] == "sequence.log shows no configAcked."
    assert row["evidence_tier"] == "LOCAL_FILE"


# ------------------------------------------------ device-facing triage report ---

REPORT = (
    "## What the device did vs what UDMI expects\n"
    "- The device published no state after the config at 12:45:05Z.\n"
    "\n"
    "## What to tell the manufacturer / what to fix on the device\n"
    "Publish a state update after every config."
)
AUDIT = (
    "## Hypothesis Resolution Audit\n"
    "- **H1: The device never published state.**\n"
    "  - Verdict: PRIMARY\n"
    "  - Rationale: no state payload after 12:45:05Z.\n"
    "- **H2: udmis dropped the state.**\n"
    "  - Verdict: REFUTED\n"
    "  - RUNTIME_EVIDENCE: NONE (source inference only)\n"
    "  - Rationale: there was no state to drop."
)


class _FakeAgent:
    """Replays the record sequence a real triage run emits, then the answer."""

    answer = REPORT + "\n\n" + AUDIT

    def run(self, prompt, context=None, stream_callback=None, event_callback=None, cancel_event=None):
        from mantis.agent import parse_audit_entries, split_audit_section

        event_callback({"type": "hypotheses", "hypotheses": ["The device never published state.", "udmis dropped the state."]})
        _, section = split_audit_section(self.answer)
        entries = parse_audit_entries(section or "", 2)
        event_callback({
            "type": "audit",
            "hypotheses": [
                {"label": "H1", "hypothesis": "The device never published state.", "verdict": "PRIMARY", **entries[1]},
                {"label": "H2", "hypothesis": "udmis dropped the state.", "verdict": "REFUTED", **entries[2]},
            ],
            "audit_section": section,
        })
        # Mirrors MantisAgent.run, which records the answer in the session history.
        from mantis.models import ChatMessage, MessageRole

        context.history.append(ChatMessage(role=MessageRole.ASSISTANT, content=self.answer, timestamp="t"))
        return self.answer


def _run_fake(tmp_path, monkeypatch, agent_cls):
    import mantis.agent

    monkeypatch.setattr(mantis.agent, "MantisAgent", agent_cls)
    store = MantisSessionStore(_StubSessionManager(str(tmp_path)))
    context = store.get_or_create("sess")
    return list(store._run_agent("prompt", context, "sess", "corr", {})), context


def test_answer_tokens_exclude_the_audit_which_arrives_as_the_collapsed_matrix(tmp_path, monkeypatch):
    events, context = _run_fake(tmp_path, monkeypatch, _FakeAgent)
    names = [e["event"] for e in events]
    # Provisional matrix during the run; the final one after the answer.
    assert names == ["hypothesis_matrix", "token", "hypothesis_matrix"]

    token = events[1]["data"]["text"]
    assert token == REPORT
    assert "Hypothesis Resolution Audit" not in token and "PRIMARY" not in token

    final = events[2]["data"]
    assert final["final"] is True
    assert final["audit_markdown"] == AUDIT
    rows = final["hypotheses"]
    assert [r["rationale"] for r in rows] == [
        "no state payload after 12:45:05Z.",
        "there was no state to drop.",
    ]
    assert [r["evidence_tier"] for r in rows] == [None, "NONE"]
    # The agent's history keeps the full answer, audit included, exactly once.
    assert context.history[-1].content == _FakeAgent.answer
    assert [m.content for m in context.history].count(_FakeAgent.answer) == 1


def test_answer_without_an_audit_record_is_delivered_whole(tmp_path, monkeypatch):
    class _PlainAgent:
        def run(self, prompt, context=None, stream_callback=None, event_callback=None, cancel_event=None):
            return "An explanation with a ## Hypothesis Resolution Audit mention inline."

    events, _ = _run_fake(tmp_path, monkeypatch, _PlainAgent)
    assert events == [{"event": "token", "data": {"text": "An explanation with a ## Hypothesis Resolution Audit mention inline."}}]


def test_audit_only_answer_is_reported_as_an_error_with_the_matrix(tmp_path, monkeypatch):
    class _AuditOnly(_FakeAgent):
        answer = AUDIT

    events, _ = _run_fake(tmp_path, monkeypatch, _AuditOnly)
    assert [e["event"] for e in events] == ["hypothesis_matrix", "error", "hypothesis_matrix"]
    assert "no report" in events[1]["data"]["message"]


def test_triage_prompt_asks_for_a_device_facing_report(tmp_path):
    site, run_dir = _site_model(tmp_path, with_run=True)
    store, context = _store_with_context(tmp_path, site)
    prompt = store._triage_prompt(context)
    for heading in (
        "## What the device did vs what UDMI expects",
        "## What to tell the manufacturer / what to fix on the device",
        "## Hypothesis Resolution Audit",
    ):
        assert heading in prompt
    assert prompt.index("What the device did") < prompt.index("## Hypothesis Resolution Audit")
    assert "sequence.md" in prompt and "schema/*.json" in prompt and "docs/" in prompt
    assert "NOT in the device" in prompt
    assert "pubber source is never evidence" in prompt


# ---------------------------------------------------------------- stop ---
class _BlockingAgent:
    """Stands in for a run in progress: it waits until told to stop."""

    started = None
    release = None

    def run(self, prompt, context=None, stream_callback=None, event_callback=None, cancel_event=None):
        from mantis.agent import MantisCancelled

        type(self).started.set()
        # Mirrors the real agent: the cancel check happens at a step boundary.
        while not cancel_event.wait(0.05):
            if type(self).release.is_set():
                return "finished"
        raise MantisCancelled("Stopped by operator before step 2.")


def _blocking_store(tmp_path, monkeypatch):
    import threading

    import mantis.agent

    _BlockingAgent.started = threading.Event()
    _BlockingAgent.release = threading.Event()
    monkeypatch.setattr(mantis.agent, "MantisAgent", _BlockingAgent)
    store = MantisSessionStore(_StubSessionManager(str(tmp_path)))
    return store, store.get_or_create("sess")


def test_stop_without_a_run_reports_not_running(tmp_path):
    store = MantisSessionStore(_StubSessionManager(str(tmp_path)))
    assert store.stop_session("sess")["status"] == "NOT_RUNNING"


def test_stop_cancels_the_running_agent_and_records_the_stop(tmp_path, monkeypatch):
    import threading

    store, context = _blocking_store(tmp_path, monkeypatch)
    events = []
    reader = threading.Thread(
        target=lambda: events.extend(store._run_agent("prompt", context, "sess", "corr", {}))
    )
    reader.start()
    assert _BlockingAgent.started.wait(5)

    assert store.stop_session("sess")["status"] == "STOPPING"
    reader.join(5)
    assert not reader.is_alive()

    assert events == [{"event": "error", "data": {"message": "Stopped by operator before step 2."}}]
    assert context.history[-1].content == "[Run stopped by operator: Stopped by operator before step 2.]"
    assert store.stop_session("sess")["status"] == "NOT_RUNNING"


def test_closing_the_stream_cancels_the_agent(tmp_path, monkeypatch):
    class _NarratingAgent(_BlockingAgent):
        def run(self, prompt, context=None, stream_callback=None, event_callback=None, cancel_event=None):
            stream_callback("Reading the sequence.")
            return super().run(prompt, context, stream_callback, event_callback, cancel_event)

    import mantis.agent

    store, context = _blocking_store(tmp_path, monkeypatch)
    monkeypatch.setattr(mantis.agent, "MantisAgent", _NarratingAgent)
    stream = store._run_agent("prompt", context, "sess", "corr", {})
    assert next(stream) == {"event": "thought", "data": {"text": "Reading the sequence."}}
    cancel_event, worker = store._runs["sess"]
    assert not cancel_event.is_set()

    # The reader goes away mid-run, as a browser disconnect does.
    stream.close()
    assert cancel_event.is_set()
    worker.join(5)
    assert not worker.is_alive()


def test_a_second_message_is_refused_while_the_previous_worker_is_alive(tmp_path, monkeypatch):
    import threading

    store, context = _blocking_store(tmp_path, monkeypatch)
    reader = threading.Thread(target=lambda: list(store._run_agent("prompt", context, "sess", "corr", {})))
    reader.start()
    assert _BlockingAgent.started.wait(5)

    refused = list(store.stream_chat_events({"session_id": "sess", "message": "again"}, "corr"))
    assert len(refused) == 1 and refused[0]["event"] == "error"
    assert "still active" in refused[0]["data"]["message"]

    _BlockingAgent.release.set()
    reader.join(5)
    assert not reader.is_alive()
