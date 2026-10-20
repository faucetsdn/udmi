"""End-to-end tests for the UDMI Workbench gateway.

These tests assert against real repository content. They deliberately avoid
starting an actual `bin/sequencer` process or contacting a model endpoint, so
the suite stays fast and hermetic while still exercising the real discovery,
sandbox, validation, and MCP paths.
"""

import json
import os
import threading
import urllib.error
import urllib.parse
import urllib.request

import pytest

from workbench.server import artifacts, discovery, sequences
from workbench.server.gateway import create_gateway
from workbench.server.logger import SERVER_LOGGER
from workbench.server.runner import (
    FEATURE_STAGE_ORDER,
    VALID_LOG_LEVELS,
    VALID_STAGES,
    SequencerRunner,
    RunnerError,
    stages_admitted,
)
from workbench.server.site_roots import SiteRootError, SiteRootRegistry

SITE_MODEL = "sites/udmi_site_model"


@pytest.fixture(scope="module")
def config_path(tmp_path_factory):
    """An isolated registry file so tests never mutate the real user config."""
    return str(tmp_path_factory.mktemp("workbench-config") / "workbench.json")


@pytest.fixture(scope="module")
def gateway(config_path):
    SERVER_LOGGER.clear()
    server = create_gateway(host="127.0.0.1", port=0, config_path=config_path)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    yield server, f"http://127.0.0.1:{server.server_address[1]}"
    server.shutdown()
    server.server_close()


@pytest.fixture(scope="module")
def server_url(gateway):
    return gateway[1]


@pytest.fixture
def external_site_model(tmp_path):
    """A real site model living outside the repository, as on WSL setups."""
    holder = tmp_path / "lab_models"
    model = holder / "external_site"
    (model / "devices" / "EM-11").mkdir(parents=True)
    (model / "cloud_iot_config.json").write_text(
        json.dumps({
            "site_name": "EXTERNAL-LAB",
            "registry_id": "EXT-REG-1",
            "iot_provider": "mqtt",
            "project_id": "//mqtt/localhost:18833",
        }),
        encoding="utf-8",
    )
    (model / "devices" / "EM-11" / "metadata.json").write_text(
        json.dumps({"system": {"hardware": {"make": "ACME", "model": "EM11"}}}),
        encoding="utf-8",
    )
    return {"holder": str(holder), "model": str(model)}


def get_json(url, correlation_id=None):
    headers = {"X-Correlation-ID": correlation_id} if correlation_id else {}
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.status, response.headers, json.loads(response.read().decode("utf-8"))


def post_json(url, payload):
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.status, json.loads(response.read().decode("utf-8"))


def delete_json(url):
    request = urllib.request.Request(url, method="DELETE")
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.status, json.loads(response.read().decode("utf-8"))


def expect_error(url, method="GET", payload=None):
    """Performs a request expected to fail and returns (status, parsed body)."""
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    headers = {"Content-Type": "application/json"} if payload is not None else {}
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30):
            pytest.fail(f"Expected an error response from {url}")
    except urllib.error.HTTPError as exc:
        return exc.code, json.loads(exc.read().decode("utf-8"))


# ------------------------------------------------------------------ health ---
def test_health_reports_real_service_identity(server_url):
    status, headers, body = get_json(f"{server_url}/api/health", "corr-health-1")

    assert status == 200
    assert headers.get("X-Correlation-ID") == "corr-health-1"
    assert body["service"] == "udmi-workbench"
    assert body["status"] == "OK"
    assert body["mcp_tools"] > 0, "MCP tool registry must be wired into the gateway"


# --------------------------------------------------------------- discovery ---
def test_site_models_are_discovered_from_disk(server_url):
    _, _, body = get_json(f"{server_url}/api/site-models")
    paths = [model["path"] for model in body["site_models"]]

    assert SITE_MODEL in paths
    for model in body["site_models"]:
        assert "device_count" in model
        assert model["name"]


def test_devices_are_discovered_with_real_metadata(server_url):
    _, _, body = get_json(f"{server_url}/api/devices?site_model={SITE_MODEL}")
    devices = {device["device_id"]: device for device in body["devices"]}

    assert "AHU-1" in devices, "udmi_site_model must expose its real AHU-1 device"
    assert devices["AHU-1"]["point_count"] > 0

    gateways = [d for d in devices.values() if d["is_gateway"]]
    assert gateways, "Site model contains a gateway device with proxy_ids"


def test_device_metadata_is_returned_verbatim(server_url):
    _, _, body = get_json(f"{server_url}/api/device?site_model={SITE_MODEL}&device_id=AHU-1")

    assert body["device_id"] == "AHU-1"
    assert "system" in body["metadata"]
    assert "pointset" in body["metadata"]


def test_sequence_catalog_merges_stage_and_bucket(server_url):
    _, _, body = get_json(f"{server_url}/api/sequences")
    sequences = body["sequences"]

    assert len(sequences) > 40, "Expected the full generated sequence catalog"
    assert all(sequence["name"] for sequence in sequences)
    assert any(sequence["stage"] for sequence in sequences)
    assert any(sequence["bucket"] for sequence in sequences), (
        "Buckets must be merged in from etc/sequencer.out"
    )


def test_vendor_variant_sequences_are_not_dropped():
    """v1's `\\w+` heading regex silently discarded every `+vendor` sequence."""
    assert sequences.SEQUENCE_HEADING_RE.match("## scan_single_future (ALPHA)")
    matched = sequences.SEQUENCE_HEADING_RE.match("## scan_single_future+vendor (ALPHA)")
    assert matched, "Sequence names containing '+' must be parsed, not skipped"
    assert matched.group(1) == "scan_single_future+vendor"


def test_results_endpoint_reports_absence_honestly(server_url):
    _, _, body = get_json(f"{server_url}/api/results?site_model={SITE_MODEL}&device_id=AHU-1")

    assert body["device_id"] == "AHU-1"
    assert isinstance(body["results"], dict)
    assert isinstance(body["results_dir_exists"], bool)
    assert body["results_dir"].endswith("out/devices/AHU-1/tests")

    for record in body["results"].values():
        assert record["status"] in {"pass", "fail", "skip", "unknown"}
        assert isinstance(record["artifacts"], list)


# ------------------------------------------------------------- fail-fast ----
def test_missing_required_query_parameter_is_rejected(server_url):
    status, body = expect_error(f"{server_url}/api/devices")

    assert status == 400
    assert "site_model" in body["error"]


def test_unknown_site_model_is_reported_not_defaulted(server_url):
    status, body = expect_error(f"{server_url}/api/devices?site_model=sites/does_not_exist")

    assert status == 404
    assert "not found" in body["error"].lower()


def test_unknown_endpoint_returns_404(server_url):
    status, body = expect_error(f"{server_url}/api/nope")

    assert status == 404
    assert "Unknown API endpoint" in body["error"]


# --------------------------------------------------------------- sandbox ----
@pytest.mark.parametrize(
    "escape_path",
    ["../../../etc/passwd", "/etc/passwd", "sites/../../../../etc/shadow"],
)
def test_file_reads_cannot_escape_the_repository(server_url, escape_path):
    status, body = expect_error(
        f"{server_url}/api/file?path={urllib.parse.quote(escape_path)}"
    )

    assert status == 403
    assert "sandbox" in body["error"].lower()


def test_real_repository_file_is_readable(server_url):
    _, _, body = get_json(
        f"{server_url}/api/file?path={SITE_MODEL}/cloud_iot_config.json"
    )

    assert body["size"] > 0
    assert json.loads(body["content"])


def test_browse_flags_site_models(server_url):
    _, _, body = get_json(f"{server_url}/api/browse?path=sites")
    flagged = [folder for folder in body["folders"] if folder["is_site_model"]]

    assert flagged, "sites/ must contain at least one recognised site model"


# -------------------------------------------------------------- sequencer ---
def test_options_endpoint_mirrors_runner_whitelists(server_url):
    _, _, body = get_json(f"{server_url}/api/sequencer/options")

    assert {o["value"] for o in body["log_levels"]} == set(VALID_LOG_LEVELS)
    assert {o["value"] for o in body["min_stages"]} == set(VALID_STAGES)


# --- alpha sequences (discovery) silently skipped by the gate ---
def test_stage_ordering_matches_the_java_enum():
    """FeatureStage ordinals: DISABLED < ALPHA < PREVIEW < BETA < STABLE."""
    assert FEATURE_STAGE_ORDER == ("DISABLED", "ALPHA", "PREVIEW", "BETA", "STABLE")


def test_default_stage_excludes_alpha_but_alpha_option_includes_it():
    # bin/sequencer's default. SequenceRunner skips anything below PREVIEW.
    assert "ALPHA" not in stages_admitted("PREVIEW")
    assert set(stages_admitted("PREVIEW")) == {"PREVIEW", "BETA", "STABLE"}

    # -a lowers the gate so alpha discovery sequences actually execute.
    assert "ALPHA" in stages_admitted("ALPHA")
    assert set(stages_admitted("ALPHA")) == {"ALPHA", "PREVIEW", "BETA", "STABLE"}

    # -x is an exact match, not a floor.
    assert stages_admitted("ALPHA_ONLY") == ["ALPHA"]


def test_options_endpoint_publishes_admitted_stages(server_url):
    _, _, body = get_json(f"{server_url}/api/sequencer/options")
    by_value = {option["value"]: option for option in body["min_stages"]}

    assert "ALPHA" not in by_value["PREVIEW"]["admits"]
    assert "ALPHA" in by_value["ALPHA"]["admits"]
    assert by_value["PREVIEW"]["label"]
    assert body["feature_stage_order"][1] == "ALPHA"


def _alpha_sequence_name(server_url):
    _, _, body = get_json(f"{server_url}/api/sequences")
    alpha = [s for s in body["sequences"] if (s.get("stage") or "").upper() == "ALPHA"]
    assert alpha, "Repository must contain at least one ALPHA sequence"
    return alpha[0]["name"]


def test_alpha_sequence_is_rejected_under_the_default_stage(server_url):
    """The reported bug: discovery sequences never ran and never reported."""
    name = _alpha_sequence_name(server_url)
    status, body = expect_error(
        f"{server_url}/api/sequencer/run",
        "POST",
        {
            "site_model": SITE_MODEL,
            "device_id": "AHU-1",
            "project_spec": "//mqtt/localhost:18833",
            "tests": [name],
            "min_stage": "PREVIEW",
        },
    )

    assert status == 400
    assert name in body["error"]
    assert "ALPHA" in body["error"]
    assert "min_stage to 'ALPHA'" in body["error"]


def test_alpha_sequence_passes_the_gate_when_stage_is_raised(server_url):
    """With -a the stage check must not fire; the request proceeds past it."""
    name = _alpha_sequence_name(server_url)
    status, body = expect_error(
        f"{server_url}/api/sequencer/run",
        "POST",
        {
            # Deliberately unresolvable so the run is refused *after* the stage
            # check, proving the stage check itself let the sequence through.
            "site_model": "sites/definitely_not_a_site_model",
            "device_id": "AHU-1",
            "project_spec": "//mqtt/localhost:18833",
            "tests": [name],
            "min_stage": "ALPHA",
        },
    )

    assert status == 404
    assert "Site model directory not found" in body["error"]
    assert "minimum stage" not in body["error"]


def test_discovery_scan_sequences_are_alpha_or_preview(server_url):
    """Guards the specific sequences reported missing from workbench runs."""
    _, _, body = get_json(f"{server_url}/api/sequences")
    scans = {s["name"]: s for s in body["sequences"] if (s.get("bucket") or "").startswith("discovery")}

    assert scans, "Discovery sequences must be present in the catalog"
    alpha_scans = [n for n, s in scans.items() if (s.get("stage") or "").upper() == "ALPHA"]
    assert alpha_scans, (
        "Expected alpha-stage discovery sequences; these are the ones the "
        "default PREVIEW gate silently drops"
    )


@pytest.mark.parametrize(
    "payload,missing",
    [
        ({}, "site_model"),
        ({"site_model": SITE_MODEL}, "device_id"),
        ({"site_model": SITE_MODEL, "device_id": "AHU-1"}, "project_spec"),
    ],
)
def test_run_requires_every_input_explicitly(server_url, payload, missing):
    status, body = expect_error(f"{server_url}/api/sequencer/run", "POST", payload)

    assert status == 400
    assert missing in body["error"]


def test_run_rejects_unknown_log_level(server_url):
    status, body = expect_error(
        f"{server_url}/api/sequencer/run",
        "POST",
        {
            "site_model": SITE_MODEL,
            "device_id": "AHU-1",
            "project_spec": "//mqtt/localhost:18833",
            "tests": ["system_mode_change"],
            "log_level": "SHOUTING",
        },
    )

    assert status == 400
    assert "SHOUTING" in body["error"]


def test_stop_requires_a_known_session(server_url):
    status, body = expect_error(
        f"{server_url}/api/sequencer/stop", "POST", {"session_id": "nonexistent"}
    )

    assert status == 400
    assert "nonexistent" in body["error"]


def test_sessions_endpoint_starts_empty(server_url):
    _, _, body = get_json(f"{server_url}/api/sequencer/sessions")

    assert isinstance(body["sessions"], list)


# ------------------------------------------------------- runner internals ---
def test_build_command_matches_the_documented_invocation(tmp_path):
    runner = SequencerRunner(str(tmp_path))
    command = runner.build_command(
        site_model_abs="/repo/sites/udmi_site_model",
        project_spec="//mqtt/localhost:18833",
        device_id="AHU-1",
        tests=["system_mode_change", "pointset_publish"],
        log_level="DEBUG",
        min_stage="ALPHA",
        serial_no="21632",
    )

    assert command == [
        "bin/sequencer",
        "-v",
        "-a",
        "-s",
        "21632",
        "/repo/sites/udmi_site_model",
        "//mqtt/localhost:18833",
        "AHU-1",
        "system_mode_change",
        "pointset_publish",
    ]


def test_build_command_rejects_unknown_stage(tmp_path):
    runner = SequencerRunner(str(tmp_path))

    with pytest.raises(RunnerError, match="GAMMA"):
        runner.build_command(
            site_model_abs="/repo/site",
            project_spec="//mqtt/localhost:18833",
            device_id="AHU-1",
            tests=[],
            log_level="INFO",
            min_stage="GAMMA",
            serial_no=None,
        )


def test_parse_events_extracts_structured_lifecycle():
    events = SequencerRunner.parse_events(
        "Starting test system_mode_change\n"
        "RESULT pass system.mode system_mode_change STABLE 5 Sequence complete\n"
        "RESULT fail discovery.scan scan_single_future+vendor ALPHA 1 Timeout\n"
        "some unrelated log line\n"
    )

    assert events[0] == {"type": "started", "test": "system_mode_change"}
    assert events[1]["type"] == "result"
    assert events[1]["result"] == "pass"
    assert events[1]["bucket"] == "system.mode"

    # The base name drives UI row lookup; the full variant is preserved.
    assert events[2]["test"] == "scan_single_future"
    assert events[2]["variant"] == "scan_single_future+vendor"
    assert events[2]["result"] == "fail"


# --------------------------------------------------------------- MCP/SPA ---
def test_mcp_tools_list_over_json_rpc(server_url):
    status, body = post_json(
        f"{server_url}/rpc", {"jsonrpc": "2.0", "id": 1, "method": "tools/list"}
    )

    assert status == 200
    assert body["jsonrpc"] == "2.0"
    assert len(body["result"]["tools"]) > 0


def test_spa_routes_serve_the_shell(server_url):
    """A reload on any shell-owned path must return the shell rather than a 404.
    `/logs` is included because the log drawer is linkable even though it is not
    a routed view."""
    for route in ("/", "/sequencer", "/devices", "/logs"):
        with urllib.request.urlopen(f"{server_url}{route}", timeout=30) as response:
            markup = response.read().decode("utf-8")
        assert response.status == 200, route
        assert 'data-role="outlet"' in markup, route


def test_removed_tab_routes_are_no_longer_shell_paths(server_url):
    """The assistant and diagnostics tabs became drawers. Continuing to serve the
    shell at their old paths would leave a stale bookmark landing on an empty
    screen with no indication that the surface moved."""
    for route in ("/assistant", "/diagnostics"):
        with pytest.raises(urllib.error.HTTPError) as caught:
            urllib.request.urlopen(f"{server_url}{route}", timeout=30)
        assert caught.value.code == 404, route


def test_frontend_assets_are_served(server_url):
    assets = (
        "/css/theme.css",
        "/css/components.css",
        "/css/drawers.css",
        "/css/compliance.css",
        "/js/app.js",
        "/js/core/api.js",
        "/js/core/store.js",
        "/js/core/logger.js",
        "/js/core/stage-gate.js",
        "/js/components/test-list.js",
        "/js/components/log-viewer.js",
        "/js/components/run-controls.js",
        "/js/components/run-summary.js",
        "/js/components/artifact-modal.js",
        "/js/components/json-viewer.js",
        "/js/components/site-roots.js",
        "/js/components/commit-dialog.js",
        "/js/components/local-setup-drawer.js",
        "/js/components/logs-drawer.js",
        "/js/components/mantis-drawer.js",
        "/js/components/mantis-panel.js",
        "/js/components/resizer.js",
        "/js/views/sequencer.js",
        "/js/views/devices.js",
    )
    for asset in assets:
        with urllib.request.urlopen(f"{server_url}{asset}", timeout=30) as response:
            assert response.status == 200, asset
            assert len(response.read()) > 0, asset


def test_shell_references_no_removed_modules():
    """A dangling import in an ES module stops the entire shell from booting, and
    the failure shows only in the browser console. Catch it here instead."""
    static_dir = os.path.abspath(
        os.path.join(os.path.dirname(__file__), "..", "static")
    )
    removed = ("views/assistant.js", "views/diagnostics.js", "AssistantView", "DiagnosticsView")
    offenders = []
    for current, _dirs, files in os.walk(static_dir):
        if "vendor" in current:
            continue
        for name in files:
            if not name.endswith((".js", ".html")):
                continue
            full = os.path.join(current, name)
            text = open(full, encoding="utf-8").read()
            for token in removed:
                if token in text:
                    offenders.append(f"{os.path.relpath(full, static_dir)} -> {token}")
    assert offenders == []


# ----------------------------------------------------------- observability ---
def test_requests_are_traced_in_the_server_ring_buffer(server_url):
    get_json(f"{server_url}/api/site-models", "corr-trace-xyz")
    _, _, body = get_json(f"{server_url}/api/diagnostics/logs?limit=500")

    assert body["count"] > 0
    for entry in body["entries"]:
        assert "timestamp" in entry
        assert "level" in entry
        assert "module" in entry


# ------------------------------------------------- consented site roots ------
def test_site_roots_start_empty_and_report_their_config_file(server_url):
    _, _, body = get_json(f"{server_url}/api/site-roots")

    assert body["site_roots"] == [], "No path is consented to until one is registered"
    assert body["config_path"].endswith("workbench.json")


def test_registering_a_path_makes_its_models_discoverable(server_url, external_site_model):
    """A site model outside the checkout must become selectable."""
    before = get_json(f"{server_url}/api/site-models")[2]["site_models"]
    assert not any(model["external"] for model in before)

    _, registered = post_json(
        f"{server_url}/api/site-roots", {"path": external_site_model["holder"]}
    )
    assert registered["site_model_count"] == 1

    _, _, after = get_json(f"{server_url}/api/site-models")
    external = [model for model in after["site_models"] if model["external"]]
    assert len(external) == 1

    model = external[0]
    assert model["path"] == external_site_model["model"], (
        "External models must be addressed by absolute path, not a ../.. relative one"
    )
    assert model["site_name"] == "EXTERNAL-LAB"
    assert model["source"] == external_site_model["holder"]

    # The repository's own models are unaffected by the registration.
    assert SITE_MODEL in [m["path"] for m in after["site_models"]]

    # And the external model is fully usable, not merely listed.
    _, _, devices = get_json(
        f"{server_url}/api/devices?site_model={urllib.parse.quote(model['path'])}"
    )
    assert [d["device_id"] for d in devices["devices"]] == ["EM-11"]

    delete_json(f"{server_url}/api/site-roots?path={urllib.parse.quote(model['source'])}")


def test_registration_refuses_a_directory_with_no_site_model(server_url, tmp_path):
    empty = tmp_path / "nothing_here"
    empty.mkdir()

    status, body = expect_error(
        f"{server_url}/api/site-roots", method="POST", payload={"path": str(empty)}
    )
    assert status == 400
    assert "No site model found" in body["error"]
    assert get_json(f"{server_url}/api/site-roots")[2]["site_roots"] == []


def test_registration_refuses_a_missing_directory(server_url, tmp_path):
    status, body = expect_error(
        f"{server_url}/api/site-roots",
        method="POST",
        payload={"path": str(tmp_path / "absent")},
    )
    assert status == 400
    assert "No such directory" in body["error"]


def test_unregistering_an_unknown_path_is_rejected(server_url, tmp_path):
    status, body = expect_error(
        f"{server_url}/api/site-roots?path={urllib.parse.quote(str(tmp_path))}",
        method="DELETE",
    )
    assert status == 400
    assert "not a registered site model path" in body["error"]


def test_withdrawing_consent_removes_the_models_again(server_url, external_site_model):
    post_json(f"{server_url}/api/site-roots", {"path": external_site_model["holder"]})
    assert get_json(f"{server_url}/api/site-models")[2]["site_models"]

    delete_json(
        f"{server_url}/api/site-roots?path="
        f"{urllib.parse.quote(external_site_model['holder'])}"
    )

    _, _, body = get_json(f"{server_url}/api/site-models")
    assert not any(model["external"] for model in body["site_models"])
    assert get_json(f"{server_url}/api/site-roots")[2]["site_roots"] == []


def test_artifact_reads_follow_consent(server_url, external_site_model):
    """An external model's files are unreadable before registration and readable after."""
    target = os.path.join(external_site_model["model"], "cloud_iot_config.json")
    encoded = urllib.parse.quote(target)

    status, body = expect_error(f"{server_url}/api/file?path={encoded}")
    assert status == 403
    assert "sandbox" in body["error"].lower()

    post_json(f"{server_url}/api/site-roots", {"path": external_site_model["holder"]})
    try:
        _, _, allowed = get_json(f"{server_url}/api/file?path={encoded}")
        assert json.loads(allowed["content"])["site_name"] == "EXTERNAL-LAB"
    finally:
        delete_json(
            f"{server_url}/api/site-roots?path="
            f"{urllib.parse.quote(external_site_model['holder'])}"
        )


def test_registry_rejects_paths_already_covered_by_the_repository(tmp_path):
    registry = SiteRootRegistry(
        os.path.abspath("."), config_path=str(tmp_path / "workbench.json")
    )
    with pytest.raises(SiteRootError, match="always scanned"):
        registry.register(SITE_MODEL)


def test_registry_rejects_a_duplicate_registration(tmp_path, external_site_model):
    registry = SiteRootRegistry(
        os.path.abspath("."), config_path=str(tmp_path / "workbench.json")
    )
    registry.register(external_site_model["holder"])
    with pytest.raises(SiteRootError, match="already registered"):
        registry.register(external_site_model["holder"])


def test_registry_reports_a_root_that_has_vanished(tmp_path, external_site_model):
    config = tmp_path / "workbench.json"
    registry = SiteRootRegistry(os.path.abspath("."), config_path=str(config))
    registry.register(external_site_model["holder"])

    os.rename(external_site_model["holder"], external_site_model["holder"] + "_moved")

    listing = discovery.list_site_models(os.path.abspath("."), registry.paths())
    assert listing["unavailable_roots"], "A vanished root must be reported, not dropped"
    assert listing["unavailable_roots"][0]["path"] == external_site_model["holder"]
    assert not any(model["external"] for model in listing["site_models"])


def test_registered_root_cannot_be_used_to_browse_upward(tmp_path, external_site_model):
    """Consent covers the registered directory, not everything above it."""
    roots = [external_site_model["holder"]]
    listing = artifacts.browse(
        os.path.abspath("."), external_site_model["holder"], roots
    )
    assert listing["parent"] is None

    with pytest.raises(artifacts.SandboxError):
        artifacts.read_text(
            os.path.abspath("."), str(tmp_path / "outside.txt"), roots
        )


def test_nested_udmi_subfolder_layout_naming_and_discovery(tmp_path):
    """Real site models frequently nest UDMI files in a udmi/ subfolder (e.g. ZZ-TEST-SITE/udmi)."""
    holder = tmp_path / "staging"
    site_model_dir = holder / "ZZ-TEST-SITE"
    udmi_dir = site_model_dir / "udmi"
    (udmi_dir / "devices" / "DDC-10").mkdir(parents=True)
    (udmi_dir / "cloud_iot_config.json").write_text(
        json.dumps({
            "site_name": "ZZ-TEST-SITE",
            "registry_id": "ZZ-TEST-SITE",
            "iot_provider": "gbos",
            "project_id": "bos-platform-staging",
        }),
        encoding="utf-8",
    )
    (udmi_dir / "devices" / "DDC-10" / "metadata.json").write_text(
        json.dumps({"system": {"hardware": {"make": "Honeywell", "model": "DDC10"}}}),
        encoding="utf-8",
    )

    udmi_root = os.path.abspath(".")

    # 1. Direct scan of the site model folder itself
    found_direct = discovery.scan_for_site_models(str(site_model_dir), udmi_root)
    assert len(found_direct) == 1
    assert found_direct[0]["name"] == "ZZ-TEST-SITE", "Must display site name ZZ-TEST-SITE, NOT udmi"
    assert found_direct[0]["device_count"] == 1

    # 2. Scan of the parent container directory (staging/)
    found_container = discovery.scan_for_site_models(str(holder), udmi_root)
    assert len(found_container) == 1
    assert found_container[0]["name"] == "ZZ-TEST-SITE"

    # 3. Resolution of both paths
    resolved_parent = discovery.resolve_site_model(udmi_root, str(site_model_dir))
    assert resolved_parent == str(udmi_dir)

    resolved_nested = discovery.resolve_site_model(udmi_root, str(udmi_dir))
    assert resolved_nested == str(udmi_dir)

    # 4. Device listing works with both parent path and nested udmi path
    devices_from_parent = discovery.list_devices(udmi_root, str(site_model_dir))
    assert len(devices_from_parent) == 1
    assert devices_from_parent[0]["device_id"] == "DDC-10"

    devices_from_nested = discovery.list_devices(udmi_root, str(udmi_dir))
    assert len(devices_from_nested) == 1
    assert devices_from_nested[0]["device_id"] == "DDC-10"


def _drain(store, payload):
    """Collects the SSE events a chat turn produces, with their event names."""
    events = list(store.stream_chat_events(payload, correlation_id="test-corr"))
    return [e["event"] for e in events], events


def test_mantis_chat_refuses_when_no_model_provider_is_configured(gateway, monkeypatch):
    """Mantis is a reasoning agent. With no provider there is no analysis to give,
    and emitting a canned report in its place is what made the previous
    implementation untrustworthy: it looked like a diagnosis and was not one. The
    stream must name the missing credential and stop."""
    from mantis.config import ProviderType
    from workbench.server import mantis_adapter

    # MANTIS_OFFLINE is the switch the refusal message tells the operator to
    # unset, so the guard is driven through it rather than through an internal.
    monkeypatch.setenv("MANTIS_OFFLINE", "true")
    assert mantis_adapter.CONFIG.provider == ProviderType.OFFLINE_DETERMINISTIC

    store = gateway[0].mantis_store
    names, events = _drain(store, {
        "session_id": "test-no-provider",
        "message": "Diagnose failure for test endpoint_connection_success",
        "context": {
            "site_model": SITE_MODEL,
            "device_id": "AHU-1",
            "test_id": "endpoint_connection_success",
        },
    })

    assert names == ["error"]
    detail = events[0]["data"]["message"]
    assert "MANTIS_OFFLINE" in detail
    assert "GEMINI_API_KEY" in detail
    # No fabricated analysis may accompany the refusal.
    assert "token" not in names
    assert "hypothesis_matrix" not in names


def test_mantis_triage_prompt_names_the_device_and_test(gateway):
    """Triage must carry the selected device and test into the prompt; inventing a
    target would send the agent to investigate the wrong failure."""
    store = gateway[0].mantis_store
    context = store.get_or_create("test-triage-prompt")
    context.active_site_model = SITE_MODEL
    context.active_device_id = "AHU-1"
    context.active_test_id = "endpoint_connection_success"

    prompt = store._triage_prompt(context)
    assert prompt is not None
    assert "AHU-1" in prompt
    assert "endpoint_connection_success" in prompt


def test_mantis_triage_prompt_refuses_without_a_target(gateway):
    """Without both a device and a test there is nothing to triage, so the adapter
    must decline rather than guess."""
    store = gateway[0].mantis_store
    context = store.get_or_create("test-triage-no-target")
    context.active_site_model = SITE_MODEL
    context.active_device_id = None
    context.active_test_id = None

    assert store._triage_prompt(context) is None


def test_mantis_translate_maps_each_agent_record_to_its_sse_event(gateway):
    """The adapter is the boundary between the agent's record vocabulary and the
    Contract 2 SSE vocabulary; a silent mismatch here renders the live view blank."""
    store = gateway[0].mantis_store
    pending = []
    metrics = {}

    def one(record):
        produced = store._translate(record, pending, metrics)
        return produced

    assert one({"type": "phase", "phase": "ACTOR"}) == [
        {"event": "phase", "data": {"phase": "ACTOR"}}
    ]

    call = one({"type": "tool_call", "call_id": "c1", "tool": "read_udmi_file",
                "args": {"path": "x.json"}})
    assert call[0]["event"] == "tool_call"
    assert call[0]["data"]["call_id"] == "c1"
    assert call[0]["data"]["tool"] == "read_udmi_file"
    assert call[0]["data"]["args"] == {"path": "x.json"}

    result = one({"type": "tool_result", "call_id": "c1", "tool": "read_udmi_file",
                  "status": "ok", "output": "{}"})
    assert result[0]["event"] == "tool_result"
    assert result[0]["data"]["call_id"] == "c1"

    # Records the adapter does not understand must be dropped, not forwarded raw.
    assert one({"type": "some_future_record"}) == []


def test_mantis_translate_marks_the_scoping_matrix_provisional(gateway):
    """The hypotheses the agent commits to at scoping time carry no verdicts yet.
    Presenting them as final would show an operator a settled-looking matrix of
    UNRESOLVED rows while the investigation is still running."""
    store = gateway[0].mantis_store
    pending = []
    events = store._translate(
        {"type": "hypotheses", "hypotheses": ["The device is offline.", "Config is stale."]},
        pending,
        {},
    )
    assert len(events) == 1
    data = events[0]["data"]
    assert events[0]["event"] == "hypothesis_matrix"
    assert data["final"] is False
    assert [h["hypothesis"] for h in data["hypotheses"]] == [
        "The device is offline.",
        "Config is stale.",
    ]
    assert {h["verdict"] for h in data["hypotheses"]} == {"UNRESOLVED"}
    # The scoping list is retained so the closing audit can be matched against it.
    assert pending == ["The device is offline.", "Config is stale."]


def test_mantis_translate_emits_final_audit_with_real_verdicts(gateway):
    """The closing audit is the only matrix that may be marked final, and a
    hypothesis the agent left unanswered must be visibly called out rather than
    quietly rendered as if it had been resolved."""
    store = gateway[0].mantis_store
    events = store._translate(
        {
            "type": "audit",
            "hypotheses": [
                {"hypothesis": "The device is offline.", "verdict": "REFUTED"},
                {"hypothesis": "Config is stale.", "verdict": "PRIMARY"},
                {"hypothesis": "Clock skew.", "verdict": None},
            ],
        },
        [],
        {},
    )
    assert len(events) == 1
    data = events[0]["data"]
    assert data["final"] is True
    assert [h["verdict"] for h in data["hypotheses"]] == [
        "REFUTED", "PRIMARY", "UNRESOLVED",
    ]
    # The agent supplied no rationale or evidence tier; none may be invented.
    assert data["hypotheses"][2]["rationale"] is None
    assert data["hypotheses"][2]["evidence_tier"] is None


def test_mantis_translate_diverts_metrics_into_the_done_payload(gateway):
    """Metrics belong on the terminating `done` event, not as a stream event of
    their own."""
    store = gateway[0].mantis_store
    sink = {}
    assert store._translate(
        {"type": "metrics", "steps": 12, "tool_calls": 26,
         "tripartite_status": "ARBITRATED", "duration_sec": 210.4},
        [],
        sink,
    ) == []
    assert sink == {
        "steps": 12,
        "tool_calls": 26,
        "tripartite_status": "ARBITRATED",
        "agent_duration_sec": 210.4,
    }


def test_testbed_status_endpoint(server_url):
    """Asserts that /api/testbed/status returns structured health and components."""
    status, headers, body = get_json(
        f"{server_url}/api/testbed/status?project_spec=%2F%2Fmqtt%2Flocalhost%3A18833"
    )
    assert status == 200
    assert "overall" in body
    assert body["overall"] in ("UP", "INITIALIZING", "DOWN", "ERROR")
    assert "components" in body
    comps = body["components"]
    assert "mqtt_broker" in comps
    assert "udmis" in comps
    assert "etcd" in comps
    assert "pubber" in comps
    assert comps["mqtt_broker"]["port"] == 18833


def test_etcd_port_follows_isolated_mode_offset():
    """Asserts the etcd probe targets the port the substrate actually binds.

    `etc/shell_common.sh` puts etcd at MQTT_PORT+1 whenever the MQTT port is
    not the privileged 8883 default. Probing a hardcoded 2379 in isolated mode
    checks a port nothing is listening on.
    """
    from workbench.server.testbed import derive_etcd_port

    assert derive_etcd_port(46432) == 46433
    assert derive_etcd_port(18833) == 18834
    assert derive_etcd_port(8883) == 2379


def test_etcd_not_reported_up_when_probe_port_is_closed(tmp_path, monkeypatch):
    """Asserts a live etcd process on some other port does not forge an UP.

    Regression for the `or is_process_running("etcd")` fallback: an etcd bound
    to a different checkout's port made this checkout report its own etcd UP.
    """
    from workbench.server import testbed as testbed_mod

    manager = testbed_mod.TestbedManager(str(tmp_path))

    monkeypatch.setattr(testbed_mod, "check_tcp_port", lambda *a, **k: False)
    monkeypatch.setattr(testbed_mod, "is_process_running", lambda pattern: pattern == "etcd")

    etcd = manager.get_status("//mqtt/localhost:46432")["components"]["etcd"]
    assert etcd["port"] == 46433
    assert etcd["probe"] == "tcp://localhost:46433"
    assert etcd["status"] == "DOWN"


def test_a_process_merely_naming_pubber_does_not_forge_an_emulator(tmp_path):
    """Asserts a dead emulator cannot read as UP because something says its name.

    Regression: the probe was `pgrep -f "bin/pubber"`, an unanchored substring
    match against every command line on the box. A `tail` of a pubber log, an
    editor, or another operator's shell was enough to report the emulator
    attached -- which is precisely the state that makes a sequencer timeout
    impossible to diagnose.
    """
    import re

    from workbench.server import testbed as testbed_mod

    manager = testbed_mod.TestbedManager(str(tmp_path))
    status = manager.get_status("//mqtt/localhost:46432")
    assert status["components"]["pubber"]["status"] == "DOWN"
    assert status["components"]["pubber"]["probe"] == "no matching process"

    rx = re.compile(testbed_mod.PUBBER_PROCESS_PATTERN)
    # Shapes that used to be treated as a running emulator:
    assert not rx.search('/bin/bash -c tail -f out/pubber.log; bin/pubber notes')
    assert not rx.search('vim /home/op/udmi/bin/pubber')
    assert not rx.search('grep -r bin/pubber .')
    # The real invocations must still match:
    assert rx.search('/bin/bash -e /home/op/udmi/bin/pubber /site //mqtt/localhost:46432 AHU-1')
    assert rx.search('/home/op/udmi/bin/pubber /site //mqtt/localhost:46432 AHU-1 1234')


def test_udmis_probe_ignores_sibling_binaries_under_the_udmis_tree(tmp_path):
    """Asserts etcd and influx living under `udmis/bin/` are not mistaken for the pod.

    Regression: the probe was `pgrep -f "udmis"`, which matched
    `udmis/bin/etcd_bin/etcd` and `bin/../udmis/bin/influx_bin/influxd`. With a
    stale `var/pod_ready.txt` sentinel that made a dead pod report UP.
    """
    import re

    from workbench.server import testbed as testbed_mod

    rx = re.compile(testbed_mod.UDMIS_PROCESS_PATTERN)
    assert not rx.search('udmis/bin/etcd_bin/etcd -listen-client-urls=http://0.0.0.0:46433')
    assert not rx.search('bin/../udmis/bin/influx_bin/influxd --http-bind-address=0.0.0.0:46434')
    assert rx.search(
        'java -XX:-OmitStackTraceInFastThrow -jar build/libs/udmis-1.0-SNAPSHOT-all.jar /x/local_pod.json'
    )


def test_testbed_status_verdict_matches_advertised_probe(server_url):
    """Asserts no component claims UP on evidence other than the probe it cites.

    Regression: etcd previously fell back to `pgrep etcd`, so it could report
    UP while the `probe` field named a TCP check that had just failed.
    """
    import socket

    _, _, body = get_json(f"{server_url}/api/testbed/status?project_spec=%2F%2Fmqtt%2Flocalhost%3A18833")
    for name, comp in body["components"].items():
        probe = comp.get("probe")
        if not probe or not probe.startswith("tcp://"):
            continue
        host, _, port = probe[len("tcp://"):].partition(":")
        reachable = False
        try:
            with socket.create_connection((host, int(port)), timeout=1.0):
                reachable = True
        except OSError:
            reachable = False
        if comp["status"] == "UP":
            assert reachable, (
                f"{name} reports UP but its advertised probe {probe} is unreachable"
            )


def test_testbed_logs_endpoint(server_url):
    """Asserts that /api/testbed/logs returns logs structure."""
    status, headers, body = get_json(f"{server_url}/api/testbed/logs?component=setup")
    assert status == 200
    assert body["component"] == "setup"
    assert "logs" in body


def test_testbed_start_requires_valid_site_model(server_url):
    """Asserts that starting the testbed fails fast if site_model is missing or invalid."""
    status, body = expect_error(
        f"{server_url}/api/testbed/start",
        method="POST",
        payload={"site_model": "non_existent_site_model_dir"},
    )
    assert status in (400, 404)
    assert "missing" in body["error"].lower() or "not found" in body["error"].lower()


def test_testbed_pubber_start_requires_device_id(server_url):
    """Asserts that starting pubber fails fast if device_id is missing."""
    status, body = expect_error(
        f"{server_url}/api/testbed/pubber/start",
        method="POST",
        payload={"site_model": SITE_MODEL, "device_id": ""},
    )
    assert status == 400
    assert "device_id" in body["error"]


def test_mantis_chat_clear_session(server_url):
    """Asserts that /api/mantis/chat/clear resets conversation history."""
    payload = {"session_id": "test-clear-session"}
    req = urllib.request.Request(
        f"{server_url}/api/mantis/chat/clear",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        assert resp.status == 200
        body = json.loads(resp.read().decode("utf-8"))
        assert body["status"] == "CLEARED"
        assert body["session_id"] == "test-clear-session"



