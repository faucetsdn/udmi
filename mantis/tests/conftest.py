"""Global pytest fixtures for Mantis test suite."""

import os
import pytest
from mantis.config import CONFIG, ProviderType


@pytest.fixture(autouse=True)
def isolate_mantis_config_and_env():
    """Ensure tests cannot leak global CONFIG state or environment variables across tests."""
    orig_provider_override = CONFIG.provider_override
    orig_flash_model = CONFIG.flash_model
    orig_pro_model = CONFIG.pro_model
    orig_thinking_level = CONFIG.thinking_level
    orig_env = dict(os.environ)

    yield

    # Restore CONFIG
    CONFIG.provider_override = orig_provider_override
    CONFIG.flash_model = orig_flash_model
    CONFIG.pro_model = orig_pro_model
    CONFIG.thinking_level = orig_thinking_level

    # Restore environment
    for k in list(os.environ.keys()):
        if k not in orig_env:
            del os.environ[k]
    for k, v in orig_env.items():
        os.environ[k] = v


@pytest.fixture(autouse=True)
def forbid_unmocked_cloud_apis(monkeypatch):
    """Guard against unintended network calls to Cloud Logging or live GenAI APIs."""
    def _refuse_cloud_logs(**kwargs):
        raise AssertionError(
            "This test reached query_cloud_logs and would hit the network. "
            "Patch 'mantis.tools.cloud_logs.query_cloud_logs' in the test."
        )

    monkeypatch.setattr("mantis.tools.cloud_logs.query_cloud_logs", _refuse_cloud_logs)

    try:
        from google import genai
        orig_client_init = genai.Client.__init__

        def _guarded_client_init(self, *args, **kwargs):
            if os.getenv("MANTIS_ALLOW_LIVE_CALLS") == "true":
                return orig_client_init(self, *args, **kwargs)
            raise AssertionError(
                "This test attempted to instantiate a real google.genai.Client without a mock. "
                "Unit tests must pass a mock client or set MANTIS_ALLOW_LIVE_CALLS=true."
            )

        monkeypatch.setattr("google.genai.Client.__init__", _guarded_client_init)
    except (ImportError, AttributeError):
        pass


@pytest.fixture(autouse=True)
def isolate_user_config(tmp_path_factory, monkeypatch):
    """Point ~/.config at an empty directory so a saved `bin/mantis setup` never leaks in."""
    monkeypatch.setenv("XDG_CONFIG_HOME", str(tmp_path_factory.mktemp("xdg")))
