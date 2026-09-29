"""Behaviour of workbench/static/js/core/desktop-notify.js under Node.

Browser globals (window.Notification, document focus/visibility, navigator)
are shimmed per scenario, and the module is imported fresh for each one so its
import-time permission watcher sees that scenario's globals.
"""

import json
import os
import shutil
import subprocess

import pytest

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
MODULE = os.path.join(REPO, "workbench", "static", "js", "core", "desktop-notify.js")

HARNESS = r"""
const results = {};
let importCount = 0;

async function scenario(name, { permission, hidden = false, focused = true, grantOnRequest }) {
  const shown = [];
  const requests = [];
  let focusedByClick = false;
  globalThis.document = { hidden, hasFocus: () => focused };
  Object.defineProperty(globalThis, "navigator", { value: {}, configurable: true, writable: true });
  const win = { focus: () => { focusedByClick = true; } };
  if (permission !== 'unsupported') {
    class FakeNotification {
      constructor(title, options) {
        this.title = title; this.options = options; this.closed = false;
        shown.push(this);
      }
      close() { this.closed = true; }
      static async requestPermission() {
        requests.push(true);
        FakeNotification.permission = grantOnRequest;
        return grantOnRequest;
      }
    }
    FakeNotification.permission = permission;
    win.Notification = FakeNotification;
  }
  globalThis.window = win;

  importCount += 1;
  const { desktopNotify } = await import(`${MODULE}?case=${importCount}`);
  const seen = [];
  desktopNotify.subscribe((s) => seen.push(s));
  const requested = await desktopNotify.requestFromGesture();
  const returned = desktopNotify.notify({ title: 'T', body: 'B', tag: 'g' });
  if (shown[0]) shown[0].onclick();
  results[name] = {
    status: desktopNotify.status(), requested, returned, seen,
    prompts: requests.length,
    shown: shown.map((n) => ({ title: n.title, ...n.options, closed: n.closed })),
    focusedByClick,
  };
}

await scenario('granted_hidden', { permission: 'granted', hidden: true });
await scenario('granted_unfocused', { permission: 'granted', focused: false });
await scenario('granted_in_front', { permission: 'granted' });
await scenario('denied_hidden', { permission: 'denied', hidden: true });
await scenario('default_then_granted', { permission: 'default', hidden: true, grantOnRequest: 'granted' });
await scenario('default_then_denied', { permission: 'default', hidden: true, grantOnRequest: 'denied' });
await scenario('unsupported', { permission: 'unsupported', hidden: true });
console.log(JSON.stringify(results));
"""


@pytest.fixture(scope="module")
def results():
    node = shutil.which("node")
    if node is None:
        pytest.fail("node is required to test workbench/static/js modules and is not on PATH.")
    script = f"const MODULE = {json.dumps('file://' + MODULE)};" + HARNESS
    done = subprocess.run([node, "--input-type=module", "-e", script],
                          capture_output=True, text=True, timeout=60, check=False)
    assert done.returncode == 0, done.stderr
    return json.loads(done.stdout.strip().splitlines()[-1])


def test_shows_when_tab_hidden_and_click_focuses_tab(results):
    r = results["granted_hidden"]
    assert r["returned"] is True
    assert r["shown"] == [{"title": "T", "body": "B", "tag": "g", "closed": True}]
    assert r["focusedByClick"] is True


def test_shows_when_tab_visible_but_another_window_is_focused(results):
    assert results["granted_unfocused"]["returned"] is True


def test_suppressed_while_tab_is_in_front(results):
    r = results["granted_in_front"]
    assert r["returned"] is False and r["shown"] == []


def test_denied_shows_nothing_and_never_reprompts(results):
    r = results["denied_hidden"]
    assert r["shown"] == [] and r["prompts"] == 0
    assert r["seen"] == ["denied"]


def test_first_gesture_prompts_once_and_publishes_result(results):
    granted = results["default_then_granted"]
    assert granted["prompts"] == 1
    assert granted["seen"] == ["default", "granted"]
    assert granted["returned"] is True
    denied = results["default_then_denied"]
    assert denied["seen"] == ["default", "denied"]
    assert denied["shown"] == []


def test_browser_without_notification_api_reports_unsupported(results):
    r = results["unsupported"]
    assert r["status"] == "unsupported" and r["seen"] == ["unsupported"]
    assert r["returned"] is False and r["prompts"] == 0
