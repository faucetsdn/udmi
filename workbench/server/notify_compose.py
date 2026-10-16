"""Composes notification emails for Mantis answers and sequencer runs (Layer 4).

Both emails carry the full result, so the inbox copy stands on its own:
  * Mantis: the question, the rendered answer with every Mermaid diagram as an
    inline PNG, and the hypothesis audit when the answer carried one.
  * Sequencer: the verdict (same rule as the run badge, `core/run-verdict.js`),
    pass/fail/skip counts, every non-passing test with its message, and the
    run's `results.md` attached.
"""

import html
import os
import re
import socket
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

import markdown2

from workbench.server.mermaid_png import MermaidRenderError, render_pngs
from workbench.server.notifications import Email

MERMAID_FENCE_RE = re.compile(r"```mermaid[ \t]*\n(.*?)```", re.DOTALL)
PLACEHOLDER = "WBDIAGRAMPLACEHOLDER{0}"
MARKDOWN_EXTRAS = ["fenced-code-blocks", "tables", "code-friendly", "cuddled-lists", "break-on-newline"]
LOG_TAIL_LINES = 40

STYLE = """
body{font-family:Roboto,Arial,sans-serif;font-size:14px;color:#202124;line-height:1.5}
h1{font-size:18px;margin:0 0 8px}h2{font-size:16px;margin:20px 0 8px}h3{font-size:14px}
code{background:#f1f3f4;padding:1px 4px;border-radius:3px;font-size:13px}
pre{background:#f1f3f4;padding:10px;border-radius:4px;overflow-x:auto;font-size:12px}
table{border-collapse:collapse;margin:8px 0}th,td{border:1px solid #dadce0;padding:4px 8px;text-align:left;vertical-align:top}
th{background:#f8f9fa}blockquote{border-left:3px solid #dadce0;margin:8px 0;padding:4px 12px;color:#5f6368}
.meta{color:#5f6368;font-size:12px}.verdict{font-weight:bold}.error{color:#b3261e}
img.diagram{max-width:100%;border:1px solid #dadce0;border-radius:4px;margin:8px 0}
"""


def _page(title: str, body: str) -> str:
    return (
        f"<!doctype html><html><head><meta charset=\"utf-8\"><title>{html.escape(title)}</title>"
        f"<style>{STYLE}</style></head><body>{body}"
        f"<p class=\"meta\">Sent by UDMI Workbench on {html.escape(socket.gethostname())} "
        "because you asked to be notified when this finished. Recipient is always the "
        "address of the Workbench operator's own Google credentials.</p></body></html>"
    )


def _shorten(text: str, limit: int = 70) -> str:
    text = " ".join(text.split())
    return text if len(text) <= limit else text[: limit - 1] + "…"


def markdown_with_diagrams(udmi_root: str, markdown_text: str) -> Dict[str, Any]:
    """Converts markdown to HTML, replacing each ```mermaid block with an inline PNG.

    Returns {"html", "images": {content_id: png}, "diagram_errors": [str]}. A diagram
    that cannot be rendered is replaced by its error message and source, never dropped.
    """
    sources = [match.group(1).strip() for match in MERMAID_FENCE_RE.finditer(markdown_text)]
    counter = iter(range(len(sources)))
    stripped = MERMAID_FENCE_RE.sub(lambda _: "\n" + PLACEHOLDER.format(next(counter)) + "\n", markdown_text)
    body = markdown2.markdown(stripped, extras=MARKDOWN_EXTRAS)

    images: Dict[str, bytes] = {}
    errors: List[str] = []
    rendered: List[Optional[bytes]] = []
    if sources:
        try:
            rendered = list(render_pngs(udmi_root, sources))
        except MermaidRenderError as exc:
            errors.append(str(exc))
            rendered = [None] * len(sources)

    for index, source in enumerate(sources):
        token = PLACEHOLDER.format(index)
        png = rendered[index] if index < len(rendered) else None
        if png is not None:
            content_id = f"diagram{index + 1}"
            images[content_id] = png
            replacement = (
                f"<img class=\"diagram\" src=\"cid:{content_id}\" alt=\"Diagram {index + 1}\">"
            )
        else:
            replacement = (
                f"<p class=\"error\">Diagram {index + 1} could not be rendered as an image: "
                f"{html.escape(errors[0] if errors else 'unknown error')}</p>"
                f"<pre>{html.escape(source)}</pre>"
            )
        body = body.replace(f"<p>{token}</p>", replacement).replace(token, replacement)
    return {"html": body, "images": images, "diagram_errors": errors}


def _context_line(context: Dict[str, Optional[str]]) -> str:
    parts = [
        f"{label}: <code>{html.escape(value)}</code>"
        for label, value in (
            ("Site model", context.get("site_model")),
            ("Device", context.get("device_id")),
            ("Test", context.get("test_id")),
        )
        if value
    ]
    return f"<p class=\"meta\">{' · '.join(parts)}</p>" if parts else ""


def _matrix_html(matrix: Optional[Dict[str, Any]]) -> str:
    if not matrix or not matrix.get("hypotheses"):
        return ""
    rows = "".join(
        "<tr>" + "".join(
            f"<td>{html.escape(str(row.get(key) or '—'))}</td>"
            for key in ("hypothesis", "verdict", "rationale", "evidence_tier")
        ) + "</tr>"
        for row in matrix["hypotheses"]
    )
    audit = matrix.get("audit_markdown")
    audit_html = markdown2.markdown(audit, extras=MARKDOWN_EXTRAS) if audit else ""
    return (
        "<h2>Hypothesis Resolution Audit</h2><table><tr><th>Hypothesis</th><th>Verdict</th>"
        f"<th>Rationale</th><th>Evidence</th></tr>{rows}</table>{audit_html}"
    )


def compose_mantis(
    udmi_root: str,
    question: str,
    context: Dict[str, Optional[str]],
    answer: Optional[str],
    matrix: Optional[Dict[str, Any]],
    error: Optional[str],
    duration_sec: float,
) -> Email:
    """Email for one finished Mantis turn: the answer, or the error that ended it."""
    minutes, seconds = divmod(int(duration_sec), 60)
    took = f"{minutes}:{seconds:02d}"
    if error:
        subject = f"Mantis failed: {_shorten(question)}"
        answer_html = f"<p class=\"error\">{html.escape(error)}</p>"
        images: Dict[str, bytes] = {}
        text_answer = f"Mantis did not produce an answer: {error}"
    else:
        subject = f"Mantis answered: {_shorten(question)}"
        converted = markdown_with_diagrams(udmi_root, answer or "")
        answer_html, images = converted["html"], converted["images"]
        text_answer = answer or ""
    body = (
        f"<h1>{html.escape(subject)}</h1>{_context_line(context)}"
        f"<p class=\"meta\">Took {took}.</p>"
        f"<h2>Question</h2><blockquote>{html.escape(question)}</blockquote>"
        f"<h2>Answer</h2>{answer_html}{_matrix_html(matrix)}"
    )
    text = f"{subject}\n\nQuestion:\n{question}\n\nAnswer:\n{text_answer}\n"
    return Email(subject, _page(subject, body), text, inline_images=images)


# ------------------------------------------------------------- sequencer ---
def run_verdict(exit_code: Optional[int], stopped: bool, counts: Dict[str, int], pending: int) -> str:
    """Server-side mirror of `core/run-verdict.js`; the badge and the email must agree."""
    if stopped:
        return "Aborted"
    settled = counts["pass"] + counts["fail"] + counts["skip"]
    clean_exit = exit_code == 0
    if not clean_exit and settled == 0:
        return "Error"
    if not clean_exit or pending > 0:
        return "Incomplete"
    if counts["fail"] > 0:
        return "Failed"
    return "Compliant"


def summarize_results(events: List[Dict[str, Any]], tests: List[str]) -> Dict[str, Any]:
    """Counts one result per test, the way the run summary badge does.

    Mirrors `store.computeMetrics` in the browser: the latest result reported
    for a test replaces any earlier one, and when tests were selected only
    those are counted. `bin/sequencer` prints its RESULT lines several times
    over and appends `schemas` pseudo-results nobody selected, so counting raw
    lines reported "pass 14" for a run of one test.

    Any verdict other than pass/skip (fail, errr, ...) counts as a failure.
    """
    latest: Dict[str, Dict[str, Any]] = {}
    for event in events:
        if event["type"] == "result":
            latest.pop(event["test"], None)
            latest[event["test"]] = event
    if tests:
        results = [latest[test] for test in tests if test in latest]
    else:
        results = list(latest.values())
    counts = {"pass": 0, "fail": 0, "skip": 0}
    failing = []
    for event in results:
        if event["result"] in ("pass", "skip"):
            counts[event["result"]] += 1
        else:
            counts["fail"] += 1
            failing.append(event)
    pending = [test for test in tests if test not in latest]
    return {"counts": counts, "failing": failing, "pending": pending, "results": results}


def compose_sequencer(session: Dict[str, Any], log_text: str, events: List[Dict[str, Any]]) -> Email:
    """Email for one finished `bin/sequencer` run."""
    tests = session.get("tests") or []
    summary = summarize_results(events, tests)
    counts = summary["counts"]
    verdict = run_verdict(session.get("exit_code"), session.get("stopped", False), counts, len(summary["pending"]))
    device = session["device_id"]
    scope = f"{len(tests)} selected test{'s' if len(tests) != 1 else ''}" if tests else "all tests"
    subject = f"Sequencer {verdict}: {device} ({scope})"

    failing_rows = "".join(
        f"<tr><td><code>{html.escape(event['variant'])}</code></td><td>{html.escape(event['result'])}</td>"
        f"<td>{html.escape(event['bucket'])}</td><td>{html.escape(event['message'])}</td></tr>"
        for event in summary["failing"]
    )
    failing_html = (
        "<h2>Tests that did not pass</h2><table><tr><th>Test</th><th>Result</th><th>Bucket</th>"
        f"<th>Message</th></tr>{failing_rows}</table>"
        if failing_rows else ""
    )
    pending_html = (
        "<h2>Selected tests that never reported a result</h2><ul>"
        + "".join(f"<li><code>{html.escape(test)}</code></li>" for test in summary["pending"])
        + "</ul>"
        if summary["pending"] else ""
    )
    tail_html = ""
    if verdict in ("Error", "Incomplete"):
        tail = "\n".join(log_text.rstrip().split("\n")[-LOG_TAIL_LINES:])
        tail_html = f"<h2>Last {LOG_TAIL_LINES} lines of sequencer output</h2><pre>{html.escape(tail)}</pre>"

    attachments = []
    results_path = os.path.join(session["site_model"], "out", "devices", device, "results.md")
    started = datetime.fromisoformat(session["started_at"])
    results_note = ""
    if os.path.isfile(results_path) and datetime.fromtimestamp(
        os.path.getmtime(results_path), timezone.utc
    ) >= started:
        with open(results_path, "rb") as fh:
            attachments.append(("results.md", fh.read(), "octet-stream"))
        results_note = "<p class=\"meta\">results.md from this run is attached.</p>"
    else:
        results_note = (
            f"<p class=\"meta\">No results.md was written by this run ({html.escape(results_path)} "
            "is missing or older than the run).</p>"
        )

    body = (
        f"<h1>{html.escape(subject)}</h1>"
        f"<p class=\"verdict\">Verdict: {html.escape(verdict)} · pass {counts['pass']} · "
        f"fail {counts['fail']} · skip {counts['skip']} · exit code {session.get('exit_code')}</p>"
        f"<p class=\"meta\">Site model: <code>{html.escape(session['site_model'])}</code><br>"
        f"Project: <code>{html.escape(session['project_spec'])}</code><br>"
        f"Started: {html.escape(session['started_at'])}<br>"
        f"Command: <code>{html.escape(session['command_line'])}</code></p>"
        f"{failing_html}{pending_html}{tail_html}{results_note}"
    )
    text_lines = [
        subject,
        f"Verdict: {verdict}  pass {counts['pass']}  fail {counts['fail']}  skip {counts['skip']}",
        f"Command: {session['command_line']}",
    ] + [f"  {e['result']} {e['variant']}: {e['message']}" for e in summary["failing"]]
    return Email(subject, _page(subject, body), "\n".join(text_lines) + "\n", attachments=attachments)
