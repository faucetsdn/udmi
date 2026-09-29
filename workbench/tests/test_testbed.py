"""Unit tests for the local testbed manager (`workbench/server/testbed.py`).

No real substrate is ever started: every test runs against a throwaway UDMI
root whose `bin/udmi` and `bin/pubber` are small shell stubs, so return codes,
log output and process-group behaviour are real while the services are not.
"""

import json
import os
import signal
import subprocess
import textwrap
import threading
import time
import urllib.error
import urllib.request

import pytest

from workbench.server import testbed as testbed_mod
from workbench.server.gateway import create_gateway

# Aliased so pytest does not try to collect the `Test*`-named classes.
CommandError = testbed_mod.TestbedCommandError
SpecError = testbed_mod.TestbedError
Manager = testbed_mod.TestbedManager
validate_local_spec = testbed_mod.validate_local_spec

SPEC = "//mqtt/localhost:46432"
DEVICE = "AHU-1"


def _write_script(path, body):
    path.write_text("#!/bin/bash\n" + textwrap.dedent(body), encoding="utf-8")
    path.chmod(0o755)


@pytest.fixture
def udmi_root(tmp_path):
    """A fake UDMI checkout with a site model and stub bin/ scripts."""
    root = tmp_path / "udmi"
    (root / "bin").mkdir(parents=True)
    site = root / "sites" / "stub_site"
    (site / "devices" / DEVICE).mkdir(parents=True)
    (site / "cloud_iot_config.json").write_text(
        json.dumps({"site_name": "STUB", "registry_id": "R", "project_id": SPEC}),
        encoding="utf-8",
    )
    return root


def _udmi_stub(root, start_rc=0, clean_rc=0, stop_rc=0):
    """bin/udmi stub: records each subcommand, prints a marker, exits as told."""
    _write_script(
        root / "bin" / "udmi",
        f"""
        echo "$1" >> "{root}/udmi_calls.txt"
        echo "$*" >> "{root}/udmi_args.txt"
        case "$1" in
          start) echo "stub start: FATAL mosquitto bind failed on $3"; exit {start_rc} ;;
          clean) echo "stub clean: cannot remove var/"; exit {clean_rc} ;;
          stop)  echo "stub stop: etcd refused to stop"; exit {stop_rc} ;;
        esac
        """,
    )


def _calls(root):
    path = root / "udmi_calls.txt"
    return path.read_text().split() if path.exists() else []


def _args(root):
    path = root / "udmi_args.txt"
    return path.read_text().splitlines() if path.exists() else []


def _wait_setup(manager, timeout=10.0):
    deadline = time.time() + timeout
    while manager.is_starting and time.time() < deadline:
        time.sleep(0.02)
    assert not manager.is_starting, "setup thread did not finish"


@pytest.fixture
def no_services(monkeypatch):
    """Nothing is listening and no substrate process exists."""
    monkeypatch.setattr(testbed_mod, "check_tcp_port", lambda *a, **k: False)
    monkeypatch.setattr(testbed_mod, "is_process_running", lambda pattern: False)
    monkeypatch.setattr(testbed_mod, "find_processes", lambda pattern: [])


# ------------------------------------------------------------ 1. return codes


def test_failed_udmi_start_reports_error_with_log_tail(udmi_root, no_services):
    _udmi_stub(udmi_root, start_rc=3)
    manager = Manager(str(udmi_root))
    manager.start("sites/stub_site", SPEC)
    _wait_setup(manager)

    assert manager.last_error is not None, "non-zero bin/udmi start went unrecorded"
    assert "bin/udmi start exited with code 3" in manager.last_error
    assert "FATAL mosquitto bind failed" in manager.last_error
    status = manager.get_status(SPEC)
    assert status["overall"] == "ERROR"
    assert "exited with code 3" in status["last_error"]


def test_failed_start_is_error_even_if_a_port_answers(udmi_root, monkeypatch):
    """A partially-up substrate after a failed start must not read INITIALIZING."""
    _udmi_stub(udmi_root, start_rc=1)
    monkeypatch.setattr(testbed_mod, "check_tcp_port", lambda host, port, **k: port == 46432)
    monkeypatch.setattr(testbed_mod, "is_process_running", lambda pattern: False)
    monkeypatch.setattr(testbed_mod, "find_processes", lambda pattern: [])
    manager = Manager(str(udmi_root))
    manager.start("sites/stub_site", SPEC)
    _wait_setup(manager)
    assert manager.get_status(SPEC)["overall"] == "ERROR"


def test_successful_start_records_no_error(udmi_root, no_services):
    _udmi_stub(udmi_root, start_rc=0)
    manager = Manager(str(udmi_root))
    manager.start("sites/stub_site", SPEC)
    _wait_setup(manager)
    assert manager.last_error is None
    assert manager.get_status(SPEC)["overall"] == "DOWN"


def test_failed_clean_aborts_start_and_reports_error(udmi_root, no_services):
    _udmi_stub(udmi_root, clean_rc=2)
    manager = Manager(str(udmi_root))
    manager.start("sites/stub_site", SPEC, clean=True)
    _wait_setup(manager)

    assert manager.last_error and "bin/udmi clean exited with code 2" in manager.last_error
    assert "cannot remove var/" in manager.last_error
    assert _calls(udmi_root) == ["clean"], "start must not run after a failed clean"
    assert manager.get_status(SPEC)["overall"] == "ERROR"


def test_failed_stop_raises_and_reports_error(udmi_root, no_services):
    _udmi_stub(udmi_root, stop_rc=4)
    manager = Manager(str(udmi_root))
    with pytest.raises(CommandError, match="bin/udmi stop exited with code 4"):
        manager.stop(SPEC)
    assert "etcd refused to stop" in manager.last_error
    assert manager.get_status(SPEC)["overall"] == "ERROR"


def test_successful_stop_clears_previous_error(udmi_root, no_services):
    _udmi_stub(udmi_root, stop_rc=0)
    manager = Manager(str(udmi_root))
    manager.last_error = "bin/udmi start exited with code 3: old failure"
    result = manager.stop(SPEC)
    assert result["status"] == "STOPPED"
    assert result["pubber"]["status"] == "NOT_RUNNING"
    assert manager.last_error is None
    assert manager.get_status(SPEC)["overall"] == "DOWN"


# --------------------------------------------------------- 2. explicit spec


@pytest.mark.parametrize(
    "spec",
    [None, "", "   ", "my-gcp-project", "//mqtt/localhost", "//mqtt/127.0.0.1:18833",
     "//mqtt/localhost:80", "//mqtt/localhost:8883", "//mqtt/localhost:70000",
     "//gbos/udmi-project"],
)
def test_non_local_spec_is_rejected(spec):
    with pytest.raises(SpecError, match="//mqtt/localhost"):
        validate_local_spec(spec)


def test_local_spec_with_explicit_port_is_accepted():
    assert validate_local_spec(" //mqtt/localhost:46432 ") == 46432


def test_start_rejects_non_local_spec_without_launching(udmi_root, no_services):
    _udmi_stub(udmi_root)
    manager = Manager(str(udmi_root))
    with pytest.raises(SpecError, match="not a local isolated-mode spec"):
        manager.start("sites/stub_site", "my-gcp-project")
    assert not manager.is_starting
    time.sleep(0.2)
    assert _calls(udmi_root) == [], "bin/udmi must not run for a rejected spec"
    assert manager.active_project_spec is None


def test_restart_rejects_bad_spec_before_stopping(udmi_root, no_services):
    _udmi_stub(udmi_root)
    manager = Manager(str(udmi_root))
    with pytest.raises(SpecError):
        manager.restart("sites/stub_site", "//mqtt/localhost")
    assert _calls(udmi_root) == []


@pytest.fixture
def stub_gateway(udmi_root, tmp_path):
    """A live gateway whose testbed manager points at the stub UDMI root."""
    server = create_gateway(host="127.0.0.1", port=0, config_path=str(tmp_path / "wb.json"))
    server.testbed = Manager(str(udmi_root))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    yield server, f"http://127.0.0.1:{server.server_address[1]}"
    server.shutdown()
    server.server_close()


def _post(url, payload):
    req = urllib.request.Request(
        url, data=json.dumps(payload).encode(), headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as exc:
        return exc.code, json.loads(exc.read())


@pytest.mark.parametrize("spec", [None, "my-gcp-project", "//mqtt/localhost"])
def test_start_endpoint_rejects_non_local_spec_with_400(stub_gateway, udmi_root, spec):
    _udmi_stub(udmi_root)
    _, url = stub_gateway
    payload = {"site_model": str(udmi_root / "sites" / "stub_site")}
    if spec is not None:
        payload["project_spec"] = spec
    status, body = _post(f"{url}/api/testbed/start", payload)
    assert status == 400
    assert "//mqtt/localhost:<port>" in body["error"] or "explicit localhost port" in body["error"]
    time.sleep(0.2)
    assert _calls(udmi_root) == []


def test_stop_endpoint_surfaces_command_failure_as_500(stub_gateway, udmi_root, no_services):
    _udmi_stub(udmi_root, stop_rc=5)
    _, url = stub_gateway
    status, body = _post(f"{url}/api/testbed/stop", {"project_spec": SPEC})
    assert status == 500
    assert "bin/udmi stop exited with code 5" in body["error"]


# ------------------------------------------------- 4. tracked pubber group


def _group_alive(pgid):
    try:
        os.killpg(pgid, 0)
        return True
    except ProcessLookupError:
        return False


@pytest.fixture
def pubber_stub(udmi_root):
    """bin/pubber stub that, like the real one, leaves a child running."""
    _write_script(udmi_root / "bin" / "pubber", "sleep 300 &\nwait\n")
    return udmi_root


@pytest.fixture
def decoy_pubber():
    """An unrelated process whose command line mentions bin/pubber."""
    proc = subprocess.Popen(
        ["bash", "-c", "exec -a /elsewhere/bin/pubber sleep 300"], start_new_session=True
    )
    time.sleep(0.2)
    yield proc
    if proc.poll() is None:
        os.killpg(proc.pid, signal.SIGKILL)
        proc.wait()


def test_stop_pubber_kills_only_the_tracked_process_group(pubber_stub, decoy_pubber, no_services):
    manager = Manager(str(pubber_stub))
    started = manager.start_pubber("sites/stub_site", DEVICE, SPEC)
    pgid = started["pid"]
    time.sleep(0.3)
    assert _group_alive(pgid)

    result = manager.stop_pubber(SPEC, DEVICE)

    assert result["status"] == "STOPPED"
    assert result["device_id"] == DEVICE
    assert not _group_alive(pgid), "the emulator's child process survived stop"
    assert decoy_pubber.poll() is None, "an unrelated bin/pubber process was killed"
    assert manager.pubber_process is None


def test_stop_pubber_with_nothing_tracked_says_so(pubber_stub, decoy_pubber, no_services):
    manager = Manager(str(pubber_stub))
    result = manager.stop_pubber(SPEC, DEVICE)
    assert result["status"] == "NOT_RUNNING"
    assert "nothing was stopped" in result["message"]
    assert decoy_pubber.poll() is None


def test_stop_pubber_for_another_device_is_rejected(pubber_stub, no_services):
    manager = Manager(str(pubber_stub))
    pgid = manager.start_pubber("sites/stub_site", DEVICE, SPEC)["pid"]
    try:
        with pytest.raises(SpecError, match=f"'{DEVICE}', not 'OTHER-9'"):
            manager.stop_pubber(SPEC, "OTHER-9")
        assert _group_alive(pgid)
    finally:
        manager.stop_pubber(SPEC)
    assert not _group_alive(pgid)


def test_stop_pubber_after_it_exited_reports_exit(udmi_root, no_services):
    _write_script(udmi_root / "bin" / "pubber", "echo boom; exit 7\n")
    manager = Manager(str(udmi_root))
    manager.start_pubber("sites/stub_site", DEVICE, SPEC)
    manager.pubber_process.wait(timeout=5)
    result = manager.stop_pubber(SPEC, DEVICE)
    assert result["status"] == "NOT_RUNNING"
    assert "already exited with code 7" in result["message"]


# ------------------------------------------- 7. spec is the per-call source


def test_status_requires_an_explicit_local_spec(udmi_root, no_services):
    manager = Manager(str(udmi_root))
    for bad in (None, "", "my-gcp-project", "//mqtt/localhost"):
        with pytest.raises(SpecError):
            manager.get_status(bad)


def test_status_ports_come_from_the_spec_passed_each_call(udmi_root, monkeypatch):
    probed = []
    monkeypatch.setattr(testbed_mod, "check_tcp_port", lambda host, port, **k: probed.append(port) or False)
    monkeypatch.setattr(testbed_mod, "is_process_running", lambda pattern: False)
    monkeypatch.setattr(testbed_mod, "find_processes", lambda pattern: [])
    manager = Manager(str(udmi_root))
    first = manager.get_status("//mqtt/localhost:46432")
    second = manager.get_status("//mqtt/localhost:51000")
    assert first["components"]["mqtt_broker"]["port"] == 46432
    assert first["components"]["etcd"]["port"] == 46433
    assert second["components"]["mqtt_broker"]["port"] == 51000
    assert second["components"]["etcd"]["port"] == 51001
    assert second["project_spec"] == "//mqtt/localhost:51000"
    assert 18833 not in probed and 18834 not in probed
    assert sorted(set(probed)) == [46432, 46433, 51000, 51001]


def test_another_specs_failure_is_not_reported_for_this_spec(udmi_root, no_services):
    _udmi_stub(udmi_root, start_rc=3)
    manager = Manager(str(udmi_root))
    manager.start("sites/stub_site", SPEC)
    _wait_setup(manager)
    assert manager.get_status(SPEC)["overall"] == "ERROR"
    other = manager.get_status("//mqtt/localhost:51000")
    assert other["overall"] == "DOWN"
    assert other["last_error"] is None
    assert other["started_project_spec"] == SPEC


def test_stop_and_clean_target_the_specs_instance(udmi_root, no_services):
    _udmi_stub(udmi_root)
    manager = Manager(str(udmi_root))
    manager.start("sites/stub_site", SPEC, clean=True)
    _wait_setup(manager)
    manager.stop(SPEC)
    args = _args(udmi_root)
    assert f"clean {SPEC}" in args
    assert f"stop {SPEC}" in args


def test_stop_requires_a_local_spec(udmi_root, no_services):
    _udmi_stub(udmi_root)
    manager = Manager(str(udmi_root))
    with pytest.raises(SpecError):
        manager.stop(None)
    assert _calls(udmi_root) == []


def test_stop_that_leaves_ports_open_is_an_error(udmi_root, monkeypatch):
    """`bin/udmi stop` always exits 0; only the probe can tell it failed."""
    _udmi_stub(udmi_root, stop_rc=0)
    monkeypatch.setattr(testbed_mod, "STOP_VERIFY_TIMEOUT_SEC", 0.2)
    monkeypatch.setattr(testbed_mod, "STOP_VERIFY_POLL_SEC", 0.05)
    monkeypatch.setattr(testbed_mod, "check_tcp_port", lambda host, port, **k: port == 46432)
    monkeypatch.setattr(testbed_mod, "is_process_running", lambda pattern: False)
    monkeypatch.setattr(testbed_mod, "find_processes", lambda pattern: [])
    manager = Manager(str(udmi_root))
    with pytest.raises(CommandError, match="MQTT tcp://localhost:46432 still accepted connections"):
        manager.stop(SPEC)
    assert manager.get_status(SPEC)["overall"] == "ERROR"


def test_stop_pubber_for_another_spec_is_rejected(pubber_stub, no_services):
    manager = Manager(str(pubber_stub))
    pgid = manager.start_pubber("sites/stub_site", DEVICE, SPEC)["pid"]
    try:
        with pytest.raises(SpecError, match="connected to"):
            manager.stop_pubber("//mqtt/localhost:51000", DEVICE)
        assert _group_alive(pgid)
    finally:
        manager.stop_pubber(SPEC)


def test_start_pubber_requires_a_local_spec(pubber_stub, no_services):
    manager = Manager(str(pubber_stub))
    with pytest.raises(SpecError):
        manager.start_pubber("sites/stub_site", DEVICE, None)
    assert manager.pubber_process is None


def _get(url):
    try:
        with urllib.request.urlopen(url, timeout=10) as resp:
            return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as exc:
        return exc.code, json.loads(exc.read())


def test_status_endpoint_requires_spec_and_uses_it(stub_gateway, no_services):
    _, url = stub_gateway
    status, body = _get(f"{url}/api/testbed/status")
    assert status == 400 and "project_spec" in body["error"]
    status, body = _get(f"{url}/api/testbed/status?project_spec=%2F%2Fmqtt%2Flocalhost%3A51000")
    assert status == 200
    assert body["components"]["mqtt_broker"]["port"] == 51000
    assert body["components"]["etcd"]["port"] == 51001


# --------------------------------------- 8. physical device connection card

from workbench.server import testbed_connection  # noqa: E402

REAL_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


@pytest.fixture
def connection_root(udmi_root, monkeypatch):
    (udmi_root / "etc").mkdir()
    (udmi_root / "etc" / "mosquitto_udmi.conf").write_text(
        open(os.path.join(REAL_ROOT, "etc", "mosquitto_udmi.conf")).read()
    )
    site = udmi_root / "sites" / "stub_site"
    dev = site / "devices" / DEVICE
    (dev / "rsa_private.pkcs8").write_bytes(b"key")
    (dev / "rsa_private.crt").write_text("cert")
    (dev / "rsa_private.pem").write_text("pem")
    (dev / "metadata.json").write_text(json.dumps({"cloud": {"auth_type": "RS256"}}))
    (site / "cloud_iot_config.json").write_text(json.dumps({"registry_id": "ZZ-TRI-FECTA"}))
    monkeypatch.setattr(
        testbed_connection, "non_loopback_ipv4",
        lambda: {"addresses": [{"interface": "eth0", "address": "10.1.2.3"}], "error": None},
    )
    return udmi_root


def test_connection_facts_are_derived_from_config_and_site(connection_root):
    facts = testbed_connection.describe_connection(
        str(connection_root), SPEC, "sites/stub_site", DEVICE
    )
    assert facts["broker"]["port"] == 46432
    assert [h["host"] for h in facts["broker"]["hosts"]] == ["10.1.2.3", "localhost"]
    assert facts["broker"]["anonymous_allowed"] is False
    assert facts["tls"]["used"] is True
    assert facts["tls"]["client_certificate_required"] is True
    assert facts["tls"]["ca_certificate"] == "sites/stub_site/reflector/ca.crt"
    assert facts["tls"]["ca_exists"] is False
    assert facts["tls"]["server_certificate_names"] == "unknown"
    assert facts["tls"]["device_certificate"] == "sites/stub_site/devices/AHU-1/rsa_private.crt"
    assert facts["identity"]["client_id"] == "/r/ZZ-TRI-FECTA/d/AHU-1"
    assert facts["identity"]["broker_account_provisioned_on_start"] is True
    assert "rsa_private.pkcs8" in facts["identity"]["password_command"]
    assert facts["topics"]["publish"] == ["/r/ZZ-TRI-FECTA/d/AHU-1/state", "/r/ZZ-TRI-FECTA/d/AHU-1/events/<subfolder>"]
    assert facts["topics"]["subscribe"] == ["/r/ZZ-TRI-FECTA/d/AHU-1/config", "/r/ZZ-TRI-FECTA/d/AHU-1/errors"]
    assert facts["device_key"]["private_key"] == "sites/stub_site/devices/AHU-1/rsa_private.pkcs8"
    assert any("binds all interfaces" in u for u in facts["unknowns"])
    assert any("server certificate" in u.lower() for u in facts["unknowns"])
    assert {d["path"] for d in facts["docs"]} >= {"docs/specs/mqtt_client.md", "docs/specs/tech_stack.md"}


def test_connection_uses_gateway_identity_and_flags_unprovisioned(connection_root):
    dev = connection_root / "sites" / "stub_site" / "devices" / DEVICE
    (dev / "metadata.json").write_text(json.dumps({"gateway": {"gateway_id": "GAT-1"}}))
    gw = connection_root / "sites" / "stub_site" / "devices" / "GAT-1"
    gw.mkdir()
    (gw / "ec_private.pkcs8").write_bytes(b"k")
    facts = testbed_connection.describe_connection(str(connection_root), SPEC, "sites/stub_site", DEVICE)
    assert facts["identity"]["client_id"] == "/r/ZZ-TRI-FECTA/d/GAT-1"
    assert facts["identity"]["via_gateway"] == "GAT-1"
    assert facts["topics"]["prefix"] == "/r/ZZ-TRI-FECTA/d/AHU-1"
    assert facts["device_key"]["private_key"].endswith("GAT-1/ec_private.pkcs8")
    assert facts["identity"]["broker_account_provisioned_on_start"] is False


def test_connection_endpoint_rejects_missing_spec(stub_gateway, connection_root):
    _, url = stub_gateway
    status, body = _get(f"{url}/api/testbed/connection?site_model=sites/stub_site&device_id={DEVICE}")
    assert status == 400 and "project_spec" in body["error"]
    q = f"project_spec=%2F%2Fmqtt%2Flocalhost%3A46432&site_model=sites/stub_site&device_id={DEVICE}"
    status, body = _get(f"{url}/api/testbed/connection?{q}")
    assert status == 200 and body["identity"]["client_id"] == "/r/ZZ-TRI-FECTA/d/AHU-1"


def test_non_loopback_ipv4_parses_ip_output(monkeypatch):
    out = (
        "1: lo    inet 127.0.0.1/8 scope host lo\\       valid_lft forever\n"
        "2: eth0    inet 10.1.2.3/24 brd 10.1.2.255 scope global eth0\\       valid_lft forever\n"
    )
    monkeypatch.setattr(
        testbed_connection.subprocess, "run",
        lambda *a, **k: subprocess.CompletedProcess(a, 0, stdout=out, stderr=""),
    )
    assert testbed_connection.non_loopback_ipv4() == {
        "addresses": [{"interface": "eth0", "address": "10.1.2.3"}], "error": None,
    }
