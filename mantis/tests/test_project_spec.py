"""Unit tests for UDMI project specification parsing and resolution."""

import json
import os
import pytest
from mantis.project_spec import (
    derive_port_from_namespace,
    format_project_spec,
    is_cloud_spec,
    normalize_project_spec,
    parse_project_spec,
    resolve_target_spec,
)


def test_parse_project_spec_empty():
    res = parse_project_spec(None)
    assert res["provider"] == "mqtt"
    assert res["project"] == "localhost"
    assert res["is_cloud"] is False


def test_parse_project_spec_gbos():
    res = parse_project_spec("//gbos/bos-platform-dev/faucetsdn")
    assert res["provider"] == "gbos"
    assert res["project"] == "bos-platform-dev"
    assert res["namespace"] == "faucetsdn"
    assert res["is_cloud"] is True


def test_parse_project_spec_gref_with_user():
    res = parse_project_spec("//gref/bos-platform-staging+dev_user")
    assert res["provider"] == "gref"
    assert res["project"] == "bos-platform-staging"
    assert res["user"] == "dev_user"
    assert res["is_cloud"] is True


def test_parse_project_spec_pubsub_with_namespace_and_user():
    res = parse_project_spec("//pubsub/bos-platform-dev/faucetsdn+debug")
    assert res["provider"] == "pubsub"
    assert res["project"] == "bos-platform-dev"
    assert res["namespace"] == "faucetsdn"
    assert res["user"] == "debug"
    assert res["is_cloud"] is True


def test_parse_project_spec_clearblade():
    res = parse_project_spec("//clearblade/my-project/my-registry")
    assert res["provider"] == "clearblade"
    assert res["project"] == "my-project"
    assert res["namespace"] == "my-registry"
    assert res["is_cloud"] is True


def test_parse_project_spec_mqtt_url():
    res = parse_project_spec("mqtt://rocket:monkey@localhost:18833/faucetsdn")
    assert res["provider"] == "mqtt"
    assert res["project"] == "localhost"
    assert res["port"] == 18833
    assert res["namespace"] == "faucetsdn"
    assert res["is_cloud"] is False


def test_parse_project_spec_bridge_host():
    res = parse_project_spec("//gbos/bos-platform-dev@mqtt.bos.goog")
    assert res["provider"] == "gbos"
    assert res["project"] == "bos-platform-dev"
    assert res["bridge_host"] == "mqtt.bos.goog"
    assert res["is_cloud"] is True

    res_jwt = parse_project_spec("//jwt/bos-platform-dev@mqtt.bos.goog")
    assert res_jwt["provider"] == "jwt"
    assert res_jwt["project"] == "bos-platform-dev"
    assert res_jwt["bridge_host"] == "mqtt.bos.goog"

    res_no_site = parse_project_spec("//mqtt/_@localhost:18833")
    assert res_no_site["provider"] == "mqtt"
    assert res_no_site["project"] == "localhost"
    assert res_no_site["bridge_host"] == "localhost:18833"
    assert res_no_site["port"] == 18833


def test_parse_project_spec_registry_suffix():
    res = parse_project_spec("//mqtt/localhost:18833%ZZ-TRI-REG")
    assert res["provider"] == "mqtt"
    assert res["project"] == "localhost"
    assert res["port"] == 18833
    assert res["registry"] == "ZZ-TRI-REG"


def test_derive_port_from_namespace():
    assert derive_port_from_namespace("btesting") == 35300
    assert derive_port_from_namespace("default") == 35950


def test_normalize_project_spec():
    assert normalize_project_spec("//mqtt/localhost:18833") == "//mqtt/localhost:18833"
    assert normalize_project_spec("mqtt://localhost:18833") == "//mqtt/localhost:18833"
    assert normalize_project_spec("localhost:18833") == "//mqtt/localhost:18833"
    assert normalize_project_spec("gbos/bos-platform-dev/faucetsdn") == "//gbos/bos-platform-dev/faucetsdn"
    assert normalize_project_spec("gref/bos-platform-staging+user") == "//gref/bos-platform-staging+user"
    assert normalize_project_spec("pubsub/bos-platform-dev") == "//pubsub/bos-platform-dev"
    assert normalize_project_spec("clearblade/proj/reg") == "//clearblade/proj/reg"
    assert normalize_project_spec("bos-platform-dev@mqtt.bos.goog") == "//gbos/bos-platform-dev@mqtt.bos.goog"
    assert normalize_project_spec("bos-platform-staging") == "//gbos/bos-platform-staging"
    assert normalize_project_spec("btesting") == "//mqtt/localhost:35300/btesting"
    assert normalize_project_spec("default") == "//mqtt/localhost:35950/default"
    assert normalize_project_spec("--") == "--"
    assert normalize_project_spec("mock-project") == "mock-project"
    assert normalize_project_spec("mock-clean") == "mock-clean"

    import pytest
    with pytest.raises(ValueError, match="Unrecognized provider"):
        normalize_project_spec("invalid_prov/my-project")
    with pytest.raises(ValueError, match="Unrecognized target project spec"):
        normalize_project_spec("my-random-project")


def test_is_cloud_spec():
    assert is_cloud_spec("//gbos/bos-platform-dev/faucetsdn") is True
    assert is_cloud_spec("//gref/bos-platform-staging+user") is True
    assert is_cloud_spec("//pubsub/bos-platform-dev") is True
    assert is_cloud_spec("//clearblade/my-project/my-reg") is True
    assert is_cloud_spec("//jwt/bos-platform-dev@mqtt.bos.goog") is True
    assert is_cloud_spec("//gcp/my-project/my-registry") is True
    assert is_cloud_spec("gcp/my-project/my-registry") is True
    assert is_cloud_spec("//mqtt/_@mqtt.bos.goog") is True
    assert is_cloud_spec("//mqtt/_@localhost:18833") is False
    assert is_cloud_spec("bos-platform-staging") is True
    assert is_cloud_spec("//mqtt/remote.broker.com:8883") is True
    assert is_cloud_spec("//mqtt/localhost:18833") is False
    assert is_cloud_spec("//mqtt/127.0.0.1:28430") is False
    assert is_cloud_spec("mqtt://localhost:18833") is False
    assert is_cloud_spec("mock-clean") is False


def test_resolve_target_spec_precedence(tmp_path, monkeypatch):
    # 1. Explicit target_spec takes highest priority
    res = resolve_target_spec(target_spec="mqtt://localhost:28430")
    assert res == "//mqtt/localhost:28430"

    # 2. Environment variable TARGET_PROJECT
    monkeypatch.setenv("TARGET_PROJECT", "//gbos/bos-platform-prod/faucetsdn")
    res = resolve_target_spec()
    assert res == "//gbos/bos-platform-prod/faucetsdn"
    monkeypatch.delenv("TARGET_PROJECT")

    # 3. Environment variable PROJECT_SPEC
    monkeypatch.setenv("PROJECT_SPEC", "//gref/bos-platform-staging+dev_user")
    res = resolve_target_spec()
    assert res == "//gref/bos-platform-staging+dev_user"
    monkeypatch.delenv("PROJECT_SPEC")

    # 4. Environment variables PROJECT_ID + IOT_PROVIDER + UDMI_NAMESPACE
    monkeypatch.setenv("PROJECT_ID", "bos-cloud-test")
    monkeypatch.setenv("IOT_PROVIDER", "pubsub")
    monkeypatch.setenv("UDMI_NAMESPACE", "ns1")
    res = resolve_target_spec()
    assert res == "//pubsub/bos-cloud-test/ns1"
    monkeypatch.delenv("PROJECT_ID")
    monkeypatch.delenv("IOT_PROVIDER")
    monkeypatch.delenv("UDMI_NAMESPACE")

    # 5. Active session info
    res = resolve_target_spec(session_info={"project_spec": "//mqtt/localhost:31230"})
    assert res == "//mqtt/localhost:31230"

    # 6. Site model with cloud_iot_config.json
    cloud_site = tmp_path / "cloud_site"
    cloud_site.mkdir()
    cfg_file = cloud_site / "cloud_iot_config.json"
    cfg_file.write_text(json.dumps({
        "iot_provider": "clearblade",
        "project_id": "cb-project-123",
        "udmi_namespace": "reg-abc",
    }), encoding="utf-8")
    res = resolve_target_spec(site_model=str(cloud_site))
    assert res == "//clearblade/cb-project-123/reg-abc"

    # 7. Default fallback
    res = resolve_target_spec()
    assert res == "//mqtt/localhost"
