"""Tests for email notifications: consent, delivery, composition, and the two run paths.

No mail is sent and no credentials are read: the Notifier takes an injected identity
and transport, and Mermaid rendering is replaced where a browser would be needed.
"""

import base64
import email
import json
import os
import stat
import threading
import time
from datetime import datetime, timedelta, timezone

import pytest

from workbench.server import notify_compose
from workbench.server.mantis_adapter import MantisSessionStore
from workbench.server.notifications import GMAIL_SEND_SCOPE, Email, NotificationError, Notifier
from workbench.server.runner import SequencerRunner

ADDRESS = "operator@example.com"


def _identity(address=ADDRESS, scopes=(GMAIL_SEND_SCOPE,)):
    return lambda: (object(), address, list(scopes))


class _Outbox:
    def __init__(self):
        self.sent = []
        self.arrived = threading.Event()

    def __call__(self, credentials, body):
        message = email.message_from_bytes(base64.urlsafe_b64decode(body["raw"]))
        self.sent.append(message)
        self.arrived.set()
        return f"msg-{len(self.sent)}"


def _notifier(tmp_path, identity=None, consent=True):
    outbox = _Outbox()
    notifier = Notifier(
        config_path=str(tmp_path / "cfg" / "workbench.json"),
        identity=identity or _identity(),
        transport=outbox,
    )
    if consent:
        notifier.set_consent(True)
    return notifier, outbox


def _wait_for_delivery(notifier, count=1, timeout=5):
    deadline = time.time() + timeout
    while time.time() < deadline:
        done = [d for d in notifier.describe()["deliveries"] if d["status"] != "SENDING"]
        if len(done) >= count:
            return done
        time.sleep(0.02)
    raise AssertionError(f"expected {count} finished deliveries, got {notifier.describe()['deliveries']}")


# ------------------------------------------------------------- consent ---
def test_consent_is_refused_without_the_gmail_send_scope(tmp_path):
    notifier, _ = _notifier(tmp_path, identity=_identity(scopes=("openid",)), consent=False)
    with pytest.raises(NotificationError, match="gmail.send"):
        notifier.set_consent(True)
    assert notifier.describe()["email"]["consented"] is False


def test_consent_is_stored_beside_other_settings_without_clobbering_them(tmp_path):
    config = tmp_path / "cfg" / "workbench.json"
    config.parent.mkdir()
    config.write_text(json.dumps({"site_roots": ["/labs"]}), encoding="utf-8")
    notifier, _ = _notifier(tmp_path)
    document = json.loads(config.read_text(encoding="utf-8"))
    assert document["site_roots"] == ["/labs"]
    assert document["notifications"]["email"]["address"] == ADDRESS
    assert notifier.describe()["email"]["ready"] is True

    notifier.set_consent(False)
    document = json.loads(config.read_text(encoding="utf-8"))
    assert document["site_roots"] == ["/labs"] and "email" not in document["notifications"]


def test_delivery_is_refused_until_consent_is_given(tmp_path):
    notifier, outbox = _notifier(tmp_path, consent=False)
    with pytest.raises(NotificationError, match="not enabled"):
        notifier.send_now("test", Email("s", "<p>h</p>", "t"))
    assert outbox.sent == []
    assert notifier.describe()["deliveries"][0]["status"] == "FAILED"


def test_a_changed_login_never_redirects_results_to_another_inbox(tmp_path):
    notifier, outbox = _notifier(tmp_path)
    notifier._identity = _identity(address="someone.else@example.com")
    with pytest.raises(NotificationError, match="now belong to someone.else"):
        notifier.require_ready()
    status = notifier.describe()["email"]
    assert status["ready"] is False and "someone.else" in status["problem"]
    with pytest.raises(NotificationError):
        notifier.send_now("test", Email("s", "<p>h</p>", "t"))
    assert outbox.sent == []


# ------------------------------------------------------------ delivery ---
def test_message_goes_to_the_operator_with_inline_images_and_attachments(tmp_path):
    notifier, outbox = _notifier(tmp_path)
    delivery = notifier.send_now(
        "mantis",
        Email(
            "Answer",
            '<img src="cid:diagram1">',
            "text",
            inline_images={"diagram1": b"\x89PNG fake"},
            attachments=[("results.md", b"# results", "octet-stream")],
        ),
    )
    assert delivery["status"] == "SENT" and delivery["message_id"] == "msg-1"
    message = outbox.sent[0]
    assert message["To"] == ADDRESS and message["From"] == ADDRESS
    assert message["Subject"] == "[UDMI Workbench] Answer"
    parts = {part.get_content_type(): part for part in message.walk()}
    assert parts["image/png"]["Content-ID"] == "<diagram1>"
    assert "results.md" in parts["application/octet-stream"]["Content-Disposition"]


def test_a_compose_failure_is_recorded_not_lost(tmp_path):
    notifier, outbox = _notifier(tmp_path)

    def broken():
        raise RuntimeError("no chromium")

    notifier.send_in_background("mantis", broken)
    [delivery] = _wait_for_delivery(notifier)
    assert delivery["status"] == "FAILED" and "no chromium" in delivery["error"]
    assert outbox.sent == []


# --------------------------------------------------------- composition ---
def test_mermaid_blocks_become_inline_images(monkeypatch):
    monkeypatch.setattr(notify_compose, "render_pngs", lambda root, sources: [b"png"] * len(sources))
    converted = notify_compose.markdown_with_diagrams(
        ".", "Intro\n\n```mermaid\nsequenceDiagram\n  A->>B: hi\n```\n\nAfter"
    )
    assert 'src="cid:diagram1"' in converted["html"]
    assert "sequenceDiagram" not in converted["html"]
    assert converted["images"] == {"diagram1": b"png"}


def test_an_unrenderable_diagram_keeps_its_source_and_the_error(monkeypatch):
    def fail(root, sources):
        raise notify_compose.MermaidRenderError("Diagram 1 is not valid Mermaid: Parse error")

    monkeypatch.setattr(notify_compose, "render_pngs", fail)
    converted = notify_compose.markdown_with_diagrams(".", "```mermaid\ngraph TD\n  A-->\n```")
    assert "Parse error" in converted["html"] and "graph TD" in converted["html"]
    assert converted["images"] == {}


@pytest.mark.parametrize(
    "exit_code, stopped, counts, pending, verdict",
    [
        (0, True, {"pass": 1, "fail": 0, "skip": 0}, 0, "Aborted"),
        (1, False, {"pass": 0, "fail": 0, "skip": 0}, 0, "Error"),
        (1, False, {"pass": 1, "fail": 0, "skip": 0}, 0, "Incomplete"),
        (0, False, {"pass": 1, "fail": 0, "skip": 0}, 1, "Incomplete"),
        (0, False, {"pass": 1, "fail": 1, "skip": 0}, 0, "Failed"),
        (0, False, {"pass": 1, "fail": 0, "skip": 1}, 0, "Compliant"),
    ],
)
def test_run_verdict_matches_the_run_badge(exit_code, stopped, counts, pending, verdict):
    assert notify_compose.run_verdict(exit_code, stopped, counts, pending) == verdict


def _session(site, started_at, tests=("a", "b")):
    return {
        "device_id": "AHU-1",
        "site_model": str(site),
        "project_spec": "//mqtt/localhost:18833",
        "tests": list(tests),
        "started_at": started_at,
        "exit_code": 0,
        "stopped": False,
        "command_line": "bin/sequencer site //mqtt/localhost:18833 AHU-1 a b",
    }


def _result(test, result, message=""):
    return {"type": "result", "test": test, "variant": test, "result": result,
            "bucket": "system", "stage": "STABLE", "score": "5", "message": message}


def test_sequencer_email_lists_failures_and_attaches_this_runs_results(tmp_path):
    started = datetime.now(timezone.utc) - timedelta(seconds=5)
    results = tmp_path / "out" / "devices" / "AHU-1" / "results.md"
    results.parent.mkdir(parents=True)
    results.write_text("# Results", encoding="utf-8")
    composed = notify_compose.compose_sequencer(
        _session(tmp_path, started.isoformat()), "",
        [_result("a", "pass"), _result("b", "errr", "Timeout <waiting>")],
    )
    assert composed.subject == "Sequencer Failed: AHU-1 (2 selected tests)"
    assert "Timeout &lt;waiting&gt;" in composed.html
    assert composed.attachments == [("results.md", b"# Results", "octet-stream")]


def test_a_stale_results_file_is_not_passed_off_as_this_runs(tmp_path):
    results = tmp_path / "out" / "devices" / "AHU-1" / "results.md"
    results.parent.mkdir(parents=True)
    results.write_text("# Old", encoding="utf-8")
    later = datetime.now(timezone.utc) + timedelta(minutes=1)
    composed = notify_compose.compose_sequencer(
        _session(tmp_path, later.isoformat(), tests=("a",)), "", [_result("a", "pass")]
    )
    assert composed.attachments == [] and "older than the run" in composed.html


#: The tail of a real `bin/sequencer ... AHU-1 valid_serial_no` log. The
#: sequencer prints its RESULT lines several times over (the result summary,
#: then RESULT.log, then schema.out) and appends `schemas` pseudo-results that
#: were never selected. Parsed line by line that is 14 `pass` events for a run
#: of one test.
ONE_TEST_LOG_TAIL = """\
Sequencer result count PASS = 1
RESULT pass schemas device_state_stable STABLE 10/10 Schema validation passed
RESULT pass schemas events_system_stable STABLE 10/10 Schema validation passed
RESULT pass schemas state_update_stable STABLE 10/10 Schema validation passed
Found 0 test failures, exit code 0
Extracting sequence results:
RESULT pass system valid_serial_no STABLE 10/10 Sequence complete
RESULT pass schemas device_state_stable STABLE 10/10 Schema validation passed
RESULT pass schemas events_system_stable STABLE 10/10 Schema validation passed
RESULT pass schemas state_update_stable STABLE 10/10 Schema validation passed
RESULT pass system valid_serial_no STABLE 10/10 Sequence complete
SCHEMA pass system valid_serial_no STABLE events_system No schema violations found
RESULT pass schemas device_state_stable STABLE 10/10 Schema validation passed
RESULT pass schemas events_system_stable STABLE 10/10 Schema validation passed
RESULT pass schemas state_update_stable STABLE 10/10 Schema validation passed
RESULT pass schemas device_state_stable STABLE 10/10 Schema validation passed
RESULT pass schemas events_system_stable STABLE 10/10 Schema validation passed
RESULT pass schemas state_update_stable STABLE 10/10 Schema validation passed
"""


def test_email_counts_one_result_per_selected_test_like_the_run_summary(tmp_path):
    events = SequencerRunner.parse_events(ONE_TEST_LOG_TAIL)
    assert len([e for e in events if e["type"] == "result"]) == 14  # the raw signature
    composed = notify_compose.compose_sequencer(
        _session(tmp_path, datetime.now(timezone.utc).isoformat(), tests=("valid_serial_no",)),
        ONE_TEST_LOG_TAIL, events,
    )
    assert composed.subject == "Sequencer Compliant: AHU-1 (1 selected test)"
    assert "pass 1 · fail 0 · skip 0" in composed.html
    assert "pass 1  fail 0  skip 0" in composed.text


def test_a_later_result_for_the_same_test_replaces_the_earlier_one(tmp_path):
    summary = notify_compose.summarize_results(
        [_result("a", "fail", "first attempt"), _result("a", "pass"), _result("b", "skip")], ["a", "b"])
    assert summary["counts"] == {"pass": 1, "fail": 0, "skip": 1}
    assert summary["failing"] == [] and summary["pending"] == []


def test_an_all_tests_run_counts_each_reported_test_once(tmp_path):
    summary = notify_compose.summarize_results(
        [_result("a", "pass"), _result("a", "pass"), _result("b", "errr", "boom")], [])
    assert summary["counts"] == {"pass": 1, "fail": 1, "skip": 0}
    assert [e["test"] for e in summary["failing"]] == ["b"]


# ------------------------------------------------------ sequencer path ---
def _repo_with_stub(tmp_path, script):
    root = tmp_path / "udmi"
    (root / "bin").mkdir(parents=True)
    stub = root / "bin" / "sequencer"
    stub.write_text(f"#!/bin/sh\n{script}\n", encoding="utf-8")
    stub.chmod(stub.stat().st_mode | stat.S_IXUSR)
    return root


def test_runner_reports_a_finished_run_to_its_exit_callback(tmp_path):
    runner = SequencerRunner(str(_repo_with_stub(
        tmp_path, "echo 'RESULT pass system a STABLE 5 Sequence complete'")))
    finished = []
    done = threading.Event()
    runner.start(str(tmp_path), "//mqtt/localhost:1", "AHU-1", ["a"],
                 on_exit=lambda summary, log: (finished.append((summary, log)), done.set()))
    assert done.wait(10)
    summary, log = finished[0]
    assert summary["exit_code"] == 0 and summary["stopped"] is False
    assert [e["result"] for e in SequencerRunner.parse_events(log)] == ["pass"]


def test_a_stopped_run_reaches_its_exit_callback_marked_stopped(tmp_path):
    runner = SequencerRunner(str(_repo_with_stub(tmp_path, "sleep 30")))
    finished = []
    done = threading.Event()
    started = runner.start(str(tmp_path), "//mqtt/localhost:1", "AHU-1", [],
                           on_exit=lambda summary, log: (finished.append(summary), done.set()))
    runner.stop(started["session_id"])
    assert done.wait(10)
    assert finished[0]["stopped"] is True


# --------------------------------------------------------- mantis path ---
class _StubSessionManager:
    def __init__(self, udmi_root):
        self.udmi_root = udmi_root


class _AnsweringAgent:
    """Finishes only once released, so the test can close the stream first."""

    release = None

    def run(self, prompt, context=None, stream_callback=None, event_callback=None, cancel_event=None):
        from mantis.agent import MantisCancelled

        stream_callback("thinking")
        while not type(self).release.wait(0.02):
            if cancel_event.is_set():
                raise MantisCancelled("Stopped by operator before step 2.")
        return "The answer."


@pytest.fixture
def answering_store(tmp_path, monkeypatch):
    import mantis.agent

    _AnsweringAgent.release = threading.Event()
    monkeypatch.setattr(mantis.agent, "MantisAgent", _AnsweringAgent)
    monkeypatch.setattr(notify_compose, "render_pngs", lambda root, sources: [b"png"] * len(sources))
    notifier, outbox = _notifier(tmp_path)
    store = MantisSessionStore(_StubSessionManager(str(tmp_path)), notifier=notifier)
    return store, notifier, outbox


def test_a_notify_run_survives_the_tab_closing_and_emails_its_answer(answering_store):
    store, notifier, outbox = answering_store
    context = store.get_or_create("sess")
    stream = store._run_agent("prompt", context, "sess", "corr", {}, notify_question="How?")
    assert next(stream)["event"] == "thought"
    cancel_event, worker = store._runs["sess"]

    stream.close()  # the browser tab goes away
    assert not cancel_event.is_set()
    _AnsweringAgent.release.set()
    worker.join(5)
    assert outbox.arrived.wait(5)
    message = outbox.sent[0]
    assert message["Subject"] == "[UDMI Workbench] Mantis answered: How?"
    html = next(p for p in message.walk() if p.get_content_type() == "text/html")
    assert "The answer." in html.get_payload(decode=True).decode()


def test_a_stopped_notify_run_sends_nothing(answering_store):
    store, notifier, outbox = answering_store
    context = store.get_or_create("sess")
    stream = store._run_agent("prompt", context, "sess", "corr", {}, notify_question="How?")
    next(stream)
    _, worker = store._runs["sess"]
    assert store.stop_session("sess")["status"] == "STOPPING"
    assert [e["event"] for e in stream] == ["error"]
    worker.join(5)
    time.sleep(0.1)
    assert outbox.sent == [] and notifier.describe()["deliveries"] == []


def test_without_notify_closing_the_tab_still_cancels(answering_store):
    store, _, outbox = answering_store
    context = store.get_or_create("sess")
    stream = store._run_agent("prompt", context, "sess", "corr", {})
    next(stream)
    cancel_event, worker = store._runs["sess"]
    stream.close()
    assert cancel_event.is_set()
    worker.join(5)
    assert outbox.sent == []


@pytest.mark.parametrize(
    "payload, expected",
    [
        ({"message": "How?", "notify": "yes"}, "must be true or false"),
        ({"message": "/status", "notify": True}, "Slash commands"),
    ],
)
def test_malformed_notify_requests_are_refused(answering_store, payload, expected):
    store, _, _ = answering_store
    events = list(store.stream_chat_events({"session_id": "sess", **payload}, "corr"))
    assert len(events) == 1 and expected in events[0]["data"]["message"]


def test_notify_is_refused_up_front_when_email_is_not_enabled(tmp_path):
    notifier, _ = _notifier(tmp_path, consent=False)
    store = MantisSessionStore(_StubSessionManager(str(tmp_path)), notifier=notifier)
    events = list(store.stream_chat_events({"session_id": "s", "message": "How?", "notify": True}, "c"))
    assert len(events) == 1 and "not enabled" in events[0]["data"]["message"]
    assert store.get_or_create("s").history == []
