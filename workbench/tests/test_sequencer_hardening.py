"""Regression tests for sequencer-workspace hardening in the Workbench server.

Covers:
  * project spec suggestions built in the only form bin/sequencer accepts
    (`//<iot_provider>/<project_id>[/<udmi_namespace>]`);
  * one malformed site model no longer breaking /api/site-models;
  * concurrent sequencer runs rejected (HTTP 409) because bin/sequencer
    writes shared files (/tmp/sequencer_config.json, out/sequencer.*).

No real `bin/sequencer` is launched: the runner is pointed at a temporary
root whose `bin/sequencer` is a stub that just sleeps.
"""

import json
import os
import stat
import threading
import urllib.error
import urllib.request

import pytest

from workbench.server import discovery
from workbench.server.gateway import create_gateway
from workbench.server.routes import classify_exception
from workbench.server.runner import RunnerBusyError, RunnerError, SequencerRunner

UDMI_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SITE_MODEL = "sites/udmi_site_model"


def _write_site(directory, config):
    os.makedirs(os.path.join(directory, "devices", "AHU-1"), exist_ok=True)
    with open(os.path.join(directory, discovery.SITE_CONFIG_FILENAME), "w", encoding="utf-8") as fh:
        if isinstance(config, str):
            fh.write(config)
        else:
            json.dump(config, fh)


def _fake_repo(tmp_path):
    """A minimal UDMI root: a `sites/` dir plus a sleeping bin/sequencer stub."""
    root = tmp_path / "udmi"
    (root / "sites").mkdir(parents=True)
    (root / "bin").mkdir()
    stub = root / "bin" / "sequencer"
    stub.write_text("#!/bin/sh\nsleep 30\n", encoding="utf-8")
    stub.chmod(stub.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    return root


# --------------------------------------------------------- project specs ---
@pytest.mark.parametrize(
    "config, expected",
    [
        # sites/udmi_site_model/cloud_iot_config.json
        ({"iot_provider": "mqtt", "project_id": "udmis"}, "//mqtt/udmis"),
        # sites/udmi_site_model_0/cloud_iot_config.json (has udmi_namespace)
        (
            {"iot_provider": "dynamic", "project_id": "this-is-not-right", "udmi_namespace": "bunny"},
            "//dynamic/this-is-not-right/bunny",
        ),
        # a staging site's cloud_iot_config.json
        ({"iot_provider": "gbos", "project_id": "bos-platform-staging"}, "//gbos/bos-platform-staging"),
        # No provider: no suggestion rather than a guessed default.
        ({"project_id": "bos-platform-staging"}, None),
        ({"iot_provider": "", "project_id": "x"}, None),
        ({"iot_provider": "gbos"}, None),
        ({"iot_provider": "gbos", "project_id": "p", "udmi_namespace": ""}, "//gbos/p"),
    ],
)
def test_build_project_spec_uses_sequencer_syntax(config, expected):
    assert discovery.build_project_spec(config) == expected


def test_site_model_descriptor_carries_a_runnable_project_spec():
    desc = discovery.describe_site_model(os.path.join(UDMI_ROOT, SITE_MODEL), UDMI_ROOT)

    assert desc["project_spec"] == "//mqtt/udmis"
    assert desc["project_spec"].startswith("//"), "bin/sequencer rejects bare project ids"


def test_site_model_without_provider_offers_no_spec(tmp_path):
    site = tmp_path / "no_provider"
    _write_site(str(site), {"project_id": "bos-platform-staging"})

    desc = discovery.describe_site_model(str(site), str(tmp_path))

    assert desc["project_spec"] is None


def test_recorded_results_never_present_a_bare_project_id_as_a_spec(tmp_path):
    """A .attr envelope carries projectId only; it must not be sold as a spec."""
    site = tmp_path / "site"
    _write_site(str(site), {"iot_provider": "gbos", "project_id": "bos-platform-staging"})
    test_dir = site / "out" / "devices" / "AHU-1" / "tests" / "system_mode_change"
    test_dir.mkdir(parents=True)
    (test_dir / "sequence.md").write_text("test passed\n", encoding="utf-8")
    (test_dir / "events_system.attr").write_text(
        json.dumps({"projectId": "bos-platform-staging", "deviceRegistryId": "ZZ-TEST-SITE"}),
        encoding="utf-8",
    )

    record = discovery.get_device_results(str(tmp_path), str(site), "AHU-1")["results"][
        "system_mode_change"
    ]

    assert "project_spec" not in record
    assert record["project_id"] == "bos-platform-staging"


# ------------------------------------------------------ malformed models ---
def test_one_malformed_site_model_does_not_break_the_listing(tmp_path):
    root = _fake_repo(tmp_path)
    _write_site(str(root / "sites" / "good_site"), {"iot_provider": "mqtt", "project_id": "udmis"})
    _write_site(str(root / "sites" / "broken_site"), "{ this is not json")

    listing = discovery.list_site_models(str(root))

    assert [model["name"] for model in listing["site_models"]] == ["good_site"]
    assert listing["unavailable_roots"] == []
    assert len(listing["invalid_models"]) == 1
    invalid = listing["invalid_models"][0]
    assert invalid["path"] == "sites/broken_site"
    assert "Invalid cloud_iot_config.json" in invalid["error"]


def test_non_object_site_config_is_reported_as_invalid(tmp_path):
    root = _fake_repo(tmp_path)
    _write_site(str(root / "sites" / "list_site"), "[]")

    listing = discovery.list_site_models(str(root))

    assert listing["site_models"] == []
    assert "expected a JSON object" in listing["invalid_models"][0]["error"]


def test_malformed_model_in_registered_root_is_isolated(tmp_path):
    root = _fake_repo(tmp_path)
    holder = tmp_path / "external"
    _write_site(str(holder / "ok"), {"iot_provider": "gbos", "project_id": "p"})
    _write_site(str(holder / "bad"), "{")

    listing = discovery.list_site_models(str(root), [str(holder)])

    assert [model["name"] for model in listing["site_models"]] == ["ok"]
    assert listing["invalid_models"][0]["path"] == str(holder / "bad")


def test_scan_for_site_models_stays_strict(tmp_path):
    """Registering a root must still fail loudly on a malformed model."""
    _write_site(str(tmp_path / "bad"), "{")

    with pytest.raises(discovery.DiscoveryError, match="Invalid cloud_iot_config.json"):
        discovery.scan_for_site_models(str(tmp_path), str(tmp_path))


# ------------------------------------------------------ concurrent runs ---
def _start(runner, device_id="AHU-1"):
    return runner.start(
        site_model_abs="/does/not/matter",
        project_spec="//mqtt/localhost:18833",
        device_id=device_id,
        tests=[],
    )


def test_runner_rejects_a_second_run_while_one_is_live(tmp_path):
    runner = SequencerRunner(str(_fake_repo(tmp_path)))
    try:
        first = _start(runner)

        with pytest.raises(RunnerBusyError) as caught:
            _start(runner, device_id="AHU-22")

        message = str(caught.value)
        assert first["session_id"] in message
        assert "AHU-1" in message
        assert "/tmp/sequencer_config.json" in message
        assert isinstance(caught.value, RunnerError)
        assert len(runner.list_sessions()) == 1, "The rejected run must not be registered"

        runner.stop(first["session_id"])
        second = _start(runner, device_id="AHU-22")
        assert second["session_id"] != first["session_id"]
    finally:
        runner.shutdown()


def test_busy_error_maps_to_http_409():
    exc = RunnerBusyError({"session_id": "abc", "device_id": "AHU-1", "started_at": "t"})
    assert classify_exception(exc) == 409
    assert classify_exception(RunnerError("other")) == 400


def test_run_endpoint_returns_409_naming_the_running_session(tmp_path):
    server = create_gateway(
        host="127.0.0.1", port=0, config_path=str(tmp_path / "workbench.json")
    )
    original_runner = server.runner
    server.runner = SequencerRunner(str(_fake_repo(tmp_path)))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    url = f"http://127.0.0.1:{server.server_address[1]}/api/sequencer/run"
    payload = json.dumps({
        "site_model": SITE_MODEL,
        "device_id": "AHU-1",
        "project_spec": "//mqtt/localhost:18833",
        "tests": [],
    }).encode("utf-8")

    def post():
        request = urllib.request.Request(
            url, data=payload, headers={"Content-Type": "application/json"}, method="POST"
        )
        return urllib.request.urlopen(request, timeout=30)

    try:
        with post() as response:
            first = json.loads(response.read().decode("utf-8"))
        assert response.status == 200

        with pytest.raises(urllib.error.HTTPError) as caught:
            post()
        assert caught.value.code == 409
        body = json.loads(caught.value.read().decode("utf-8"))
        assert first["session_id"] in body["error"]
        assert "RunnerBusyError" in body["error"]
    finally:
        server.shutdown()
        server.runner.shutdown()
        server.runner = original_runner
        server.server_close()
