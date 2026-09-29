"""Unit tests for mantis.tools.mqtt."""

from unittest.mock import MagicMock, patch
import pytest

from mantis.session import SessionManager
from mantis.tools.mqtt import publish_mqtt_message


def test_publish_mqtt_message_local(monkeypatch):
    mgr = SessionManager()
    mgr.get_session_info = MagicMock(return_value={
        "ports": {"mqtt": 28430},
        "credentials": {"username": "rocket", "password": "monkey"},
        "project_spec": "//mqtt/localhost:28430",
    })

    mock_publish = MagicMock()
    monkeypatch.setattr("paho.mqtt.publish.single", mock_publish)

    res = publish_mqtt_message(
        session_mgr=mgr,
        test_id="test_local",
        topic="/r/default/d/AHU-1/state",
        payload='{"timestamp": "2026-09-14T12:00:00Z"}',
    )

    assert res["status"] == "PUBLISHED"
    assert res["is_cloud"] is False
    assert res["topic"] == "/r/default/d/AHU-1/state"
    mock_publish.assert_called_once_with(
        topic="/r/default/d/AHU-1/state",
        payload='{"timestamp": "2026-09-14T12:00:00Z"}',
        hostname="localhost",
        port=28430,
        auth={"username": "rocket", "password": "monkey"},
    )


def test_publish_mqtt_message_cloud_gbos(monkeypatch):
    mgr = SessionManager()
    mgr.get_session_info = MagicMock(return_value={})

    import sys
    mock_conn = MagicMock()
    mock_msg_conn_cls = MagicMock(return_value=mock_conn)
    mock_conn_mod = MagicMock()
    mock_conn_mod.MessageConnection = mock_msg_conn_cls
    monkeypatch.setitem(sys.modules, "udmi.common.connection", mock_conn_mod)

    res = publish_mqtt_message(
        session_mgr=mgr,
        test_id="test_cloud",
        topic="events/pointset",
        payload='{"points": {}}',
        target_spec="//gbos/bos-platform-dev/faucetsdn",
        site_model="sites/udmi_site_model",
        device_id="AHU-1",
    )

    assert res["status"] == "PUBLISHED"
    assert res["is_cloud"] is True
    assert res["provider"] == "gbos"
    mock_msg_conn_cls.assert_called_once_with(
        conn_spec="//gbos/bos-platform-dev/faucetsdn",
        site_model="sites/udmi_site_model",
        device_id="AHU-1",
    )
    mock_conn.publish_messages.assert_called_once_with([("events/pointset", '{"points": {}}')])


def test_publish_mqtt_message_cloud_pubsub(monkeypatch):
    mgr = SessionManager()
    mgr.get_session_info = MagicMock(return_value={})

    mock_pub_client = MagicMock()
    mock_future = MagicMock()
    mock_future.result.return_value = "msg-12345"
    mock_pub_client.publish.return_value = mock_future

    import sys
    mock_pubsub_mod = MagicMock()
    mock_pubsub_mod.PublisherClient.return_value = mock_pub_client
    monkeypatch.setitem(sys.modules, "google.cloud.pubsub_v1", mock_pubsub_mod)

    res = publish_mqtt_message(
        session_mgr=mgr,
        test_id="test_pubsub",
        topic="udmi_target_topic",
        payload='{"data": "test"}',
        target_spec="//pubsub/bos-platform-dev/faucetsdn",
    )

    assert res["status"] == "PUBLISHED"
    assert res["is_cloud"] is True
    assert res["provider"] == "pubsub"
    assert res["message_id"] == "msg-12345"
    mock_pub_client.publish.assert_called_once_with(
        "projects/bos-platform-dev/topics/udmi_target_topic",
        b'{"data": "test"}',
    )


def test_publish_mqtt_message_no_port_error():
    mgr = SessionManager()
    mgr.get_session_info = MagicMock(return_value={})

    res = publish_mqtt_message(
        session_mgr=mgr,
        test_id="unknown_test",
        topic="/r/default/d/AHU-1/state",
        payload='{"timestamp": "2026-09-14T12:00:00Z"}',
        target_spec="//mqtt/localhost",
    )
    assert res["status"] == "ERROR"
    assert "No MQTT port specified" in res["error"]


def test_publish_mqtt_message_anonymous(monkeypatch):
    mgr = SessionManager()
    mgr.get_session_info = MagicMock(return_value={
        "ports": {"mqtt": 18833},
        "project_spec": "//mqtt/localhost:18833",
    })

    mock_publish = MagicMock()
    monkeypatch.setattr("paho.mqtt.publish.single", mock_publish)

    res = publish_mqtt_message(
        session_mgr=mgr,
        test_id="test_anon",
        topic="/r/default/d/AHU-1/state",
        payload='{}',
    )
    assert res["status"] == "PUBLISHED"
    mock_publish.assert_called_once_with(
        topic="/r/default/d/AHU-1/state",
        payload='{}',
        hostname="localhost",
        port=18833,
        auth=None,
    )

