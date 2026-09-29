"""Behavioural tests for the Local Setup drawer (`local-setup-drawer.js`).

The drawer's methods are driven from Node with stub `api`/`store` objects, so
the real control flow runs without a browser or a live substrate.
"""

import json
import os
import shutil
import subprocess

import pytest

DRAWER = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "static", "js", "components", "local-setup-drawer.js")
)

NODE = shutil.which("node")
pytestmark = pytest.mark.skipif(NODE is None, reason="node is required to exercise local-setup-drawer.js")

HARNESS = r"""
import { LocalSetupDrawer } from 'file://__DRAWER__';

const scenario = JSON.parse(process.argv[process.argv.length - 1]);
const events = [];
let statusCalls = 0;
const state = {
  siteModel: 'sites/udmi_site_model', deviceId: 'AHU-1', pubberMode: scenario.pubberMode,
  projectSpec: scenario.projectSpec, serialNo: '1234',
};
const fake = Object.assign(Object.create(LocalSetupDrawer.prototype), {
  store: { getState: () => state, update: (action, patch) => { Object.assign(state, patch); events.push(`store:${action}`); } },
  api: {
    startTestbed: async (args) => { events.push(`startTestbed:${args.projectSpec}`); return { status: 'INITIALIZING' }; },
    restartTestbed: async (args) => { events.push(`restartTestbed:${args.projectSpec}`); return { status: 'INITIALIZING' }; },
    getTestbedStatus: async (spec) => {
      events.push(`statusSpec:${spec}`);
      const overall = scenario.statuses[Math.min(statusCalls++, scenario.statuses.length - 1)];
      events.push(`status:${overall}`);
      return { overall, last_error: overall === 'ERROR' ? 'bin/udmi start exited with code 3' : null, components: {} };
    },
    startPubber: async (args) => { events.push(`startPubber:${args.deviceId}`); events.push(`pubberSpec:${args.projectSpec}`); return { status: 'RUNNING' }; },
    getTestbedLogs: async () => ({ logs: '' }),
  },
  setAlert: (text, isError) => { if (text) events.push(`${isError ? 'error' : 'alert'}:${text}`); },
  fetchLogs: () => {}, pollStatus: () => {}, render: () => {},
  localSpec: scenario.localSpec,
  container: { querySelector: () => null },
});
// Scenario timing: make the readiness sleep instant so polling is observable.
const realSetTimeout = globalThis.setTimeout;
globalThis.setTimeout = (fn, ms) => realSetTimeout(fn, ms >= 2000 && ms < 5000 ? 0 : ms);

await fake[scenario.method]();
console.log(JSON.stringify(events));
process.exit(0);
"""

RENDER_HARNESS = r"""
import { LocalSetupDrawer } from 'file://__DRAWER__';
const canvas = { innerHTML: '' };
const fake = { container: { querySelector: (sel) => (sel === '#topology-canvas' ? canvas : null) } };
LocalSetupDrawer.prototype.renderTopology.call(
  fake, { deviceId: 'AHU-1', pubberMode: false }, { mqtt_broker: { status: 'UP', port: 46432 } }
);
console.log(JSON.stringify(canvas.innerHTML));
process.exit(0);
"""

TIMEOUT_HARNESS = r"""
import { waitForSubstrateUp } from 'file://__DRAWER__';
let clock = 0;
let calls = 0;
try {
  await waitForSubstrateUp({
    getStatus: async () => { calls++; return { overall: 'INITIALIZING' }; },
    sleep: async (ms) => { clock += ms; },
    now: () => clock,
  });
  console.log(JSON.stringify({ resolved: true }));
} catch (e) {
  console.log(JSON.stringify({ error: e.message, clock, calls }));
}
process.exit(0);
"""


def _node(script, *args):
    result = subprocess.run(
        [NODE, "--input-type=module", "-e", script.replace("__DRAWER__", DRAWER), *args],
        capture_output=True, text=True, timeout=30,
    )
    assert result.returncode == 0, result.stderr
    return json.loads(result.stdout.strip().splitlines()[-1])


LOCAL_SPEC = "//mqtt/localhost:46432"


def _run(method, statuses, pubber_mode=True, local_spec=LOCAL_SPEC, sequencer_spec="my-cloud-project"):
    scenario = {"method": method, "statuses": statuses, "pubberMode": pubber_mode,
                "localSpec": local_spec, "projectSpec": sequencer_spec}
    return _node(HARNESS, json.dumps(scenario))


@pytest.mark.parametrize("method", ["handleStart", "handleRestart"])
def test_pubber_launches_only_after_substrate_reports_up(method):
    events = _run(method, ["INITIALIZING", "INITIALIZING", "UP"])
    assert "startPubber:AHU-1" in events
    pubber_at = events.index("startPubber:AHU-1")
    up_at = events.index("status:UP")
    assert up_at < pubber_at, f"Pubber launched before substrate was UP: {events}"


@pytest.mark.parametrize("method", ["handleStart", "handleRestart"])
def test_substrate_error_blocks_pubber_and_surfaces_last_error(method):
    events = _run(method, ["INITIALIZING", "ERROR"])
    assert not any(e.startswith("startPubber") for e in events), events
    errors = [e for e in events if e.startswith("error:")]
    assert errors and "bin/udmi start exited with code 3" in errors[-1], events


@pytest.mark.parametrize("method", ["handleStart", "handleRestart"])
def test_blank_local_spec_blocks_start_without_defaulting(method):
    events = _run(method, ["UP"], local_spec="")
    assert not any(e.startswith(("startTestbed", "restartTestbed", "startPubber")) for e in events), events
    assert any(e.startswith("error:") and "//mqtt/localhost:<port>" in e for e in events), events
    assert not any("18833" in e for e in events), events


@pytest.mark.parametrize("method", ["handleStart", "handleRestart"])
def test_drawer_spec_is_used_for_every_call_not_the_sequencer_spec(method):
    events = _run(method, ["INITIALIZING", "UP"], sequencer_spec="//mqtt/localhost:50000")
    verb = "startTestbed" if method == "handleStart" else "restartTestbed"
    assert f"{verb}:{LOCAL_SPEC}" in events, events
    assert all(e == f"statusSpec:{LOCAL_SPEC}" for e in events if e.startswith("statusSpec:")), events
    assert f"pubberSpec:{LOCAL_SPEC}" in events, events
    assert not any("50000" in e for e in events if not e.startswith(("alert:", "error:"))), events


def test_physical_mode_by_default_never_launches_pubber():
    # pubberMode unset (the store default) must mean physical hardware.
    events = _run("handleStart", ["UP"], pubber_mode=None)
    assert not any(e.startswith("startPubber") for e in events), events


def test_readiness_wait_hard_stops_at_90_seconds():
    out = _node(TIMEOUT_HARNESS)
    assert "resolved" not in out
    assert out["clock"] == 90000
    assert "not UP after 90s" in out["error"]
    assert "Pubber was not launched" in out["error"]


def test_physical_mode_does_not_claim_connection():
    html = _node(RENDER_HARNESS)
    assert "CONNECTED" not in html
    assert "badge-up\">NOT" not in html
    assert "NOT MONITORED" in html
    assert "not monitored" in html


STORE_HARNESS = r"""
globalThis.localStorage = { getItem: () => null, setItem: () => {} };
globalThis.window = { location: { search: '', pathname: '/' }, history: { replaceState: () => {} } };
const { WorkspaceStore } = await import('file://__STORE__');
console.log(JSON.stringify({ pubberMode: new WorkspaceStore().getState().pubberMode }));
process.exit(0);
"""


def test_store_defaults_to_physical_device_mode():
    store = os.path.join(os.path.dirname(DRAWER), "..", "core", "store.js")
    out = _node(STORE_HARNESS.replace("__STORE__", os.path.abspath(store)))
    assert out["pubberMode"] is False


MISMATCH_HARNESS = r"""
import { specMismatch } from 'file://__DRAWER__';
const L = '//mqtt/localhost:46432';
console.log(JSON.stringify({
  differentLocal: specMismatch('//mqtt/localhost:50000', L),
  same: specMismatch(L, L),
  cloud: specMismatch('my-gcp-project', L),
  blank: specMismatch('', L),
  noLocal: specMismatch('//mqtt/localhost:50000', ''),
}));
process.exit(0);
"""


def test_mismatch_warning_only_for_a_different_local_broker():
    out = _node(MISMATCH_HARNESS)
    assert "//mqtt/localhost:50000" in out["differentLocal"] and "46432" in out["differentLocal"]
    assert out["same"] == out["cloud"] == out["blank"] == out["noLocal"] == ""


POLL_HARNESS = r"""
import { LocalSetupDrawer } from 'file://__DRAWER__';
const calls = [];
let stored = null;
const fake = Object.assign(Object.create(LocalSetupDrawer.prototype), {
  localSpec: process.argv[process.argv.length - 1],
  api: { getTestbedStatus: async (spec) => { calls.push(spec);
    const e = new Error('Project spec is not a local isolated-mode spec'); e.status = 400; throw e; } },
  store: { update: (a, patch) => { stored = patch.testbedStatus; } },
  render: () => {},
});
await fake.pollStatus();
console.log(JSON.stringify({ calls, stored }));
process.exit(0);
"""


@pytest.mark.parametrize("spec,expect_call", [("", False), ("bogus", True)])
def test_status_poll_without_valid_spec_reports_unknown(spec, expect_call):
    out = _node(POLL_HARNESS, spec)
    assert out["calls"] == ([spec] if expect_call else [])
    assert out["stored"]["overall"] == "UNKNOWN"
    assert out["stored"]["components"] == {}
    assert out["stored"]["spec_error"]


CARD_HARNESS = r"""
import { renderConnectionCard } from 'file://__CARD__';
const data = JSON.parse(process.argv[process.argv.length - 1]);
console.log(JSON.stringify(renderConnectionCard({ data })));
process.exit(0);
"""


def test_connection_card_renders_backend_facts_and_unknowns():
    card = os.path.join(os.path.dirname(DRAWER), "device-connection-card.js")
    data = {
        "broker": {"hosts": [{"host": "10.1.2.3", "interface": "eth0", "in_server_cert": False},
                             {"host": "localhost", "interface": "loopback", "in_server_cert": True}],
                   "port": 46432},
        "tls": {"used": True, "client_certificate_required": True, "ca_certificate": "sites/s/reflector/ca.crt",
                "ca_exists": True, "device_certificate": "unknown", "device_private_key_pem": "unknown"},
        "identity": {"client_id": "/r/ZZ-TRI-FECTA/d/AHU-1", "password_rule": "first 8 hex",
                     "password_command": "sha256sum < k | head -c 8", "via_gateway": None,
                     "provisioning_note": "note"},
        "topics": {"publish": ["/r/ZZ-TRI-FECTA/d/AHU-1/state"], "subscribe": ["/r/ZZ-TRI-FECTA/d/AHU-1/config"]},
        "device_key": {"private_key": "sites/s/devices/AHU-1/rsa_private.pkcs8"},
        "docs": [{"path": "docs/specs/mqtt_client.md", "title": "t"}],
        "unknowns": ["Whether mosquitto binds all interfaces"],
    }
    html = _node(CARD_HARNESS.replace("__CARD__", card), json.dumps(data))
    for needle in ["10.1.2.3", "46432", "TLS (ssl)", "/r/ZZ-TRI-FECTA/d/AHU-1", "reflector/ca.crt",
                   "in server cert: no", "rsa_private.pkcs8", "docs/specs/mqtt_client.md",
                   "Whether mosquitto binds all interfaces", "unknown",
                   'href="/api/repo-doc?path=docs%2Fspecs%2Fmqtt_client.md"',
                   'target="_blank" rel="noopener noreferrer"']:
        assert needle in html, needle
