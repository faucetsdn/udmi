"""Unit tests for mantis.tools.site_models."""

import os
import json
import pytest
from mantis.tools.site_models import inspect_site_model, sanitize_credentials


def test_sanitize_credentials():
    raw = {
        "username": "admin",
        "password": "super_secret_password",
        "key_data": "-----BEGIN RSA PRIVATE KEY-----\nABCDEF\n-----END RSA PRIVATE KEY-----",
        "url": "mqtt://user:pass123@localhost:1883",
        "nested": {
            "token": "secret_token_val",
            "normal_field": "hello",
        },
    }
    sanitized = sanitize_credentials(raw)
    assert sanitized["password"] == "***REDACTED***"
    assert sanitized["nested"]["token"] == "***REDACTED***"
    assert sanitized["nested"]["normal_field"] == "hello"
    assert "pass123" not in sanitized["url"]


def test_inspect_site_model_summary():
    res = inspect_site_model("sites/udmi_site_model")
    assert res["status"] == "SUCCESS"
    assert "devices" in res
    assert res["device_count"] > 0
    assert "AHU-1" in res["devices"]


def test_inspect_site_model_device():
    res = inspect_site_model("sites/udmi_site_model", device_id="AHU-1")
    assert res["status"] == "SUCCESS"
    assert res["device_id"] == "AHU-1"
    assert "metadata" in res
    assert "point_count" in res


def test_inspect_site_model_nonexistent_device():
    res = inspect_site_model("sites/udmi_site_model", device_id="NONEXISTENT_DEVICE_123")
    assert res["status"] == "ERROR"
    assert "not found" in res["error"]
