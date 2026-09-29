"""Unit tests for mantis.config."""

import os
from mantis.config import MantisConfig, ModelTier, ProviderType


def test_config_defaults():
    cfg = MantisConfig()
    assert cfg.flash_model == "gemini-3.7-flash"
    assert cfg.pro_model == "gemini-3.1-pro-preview"
    assert cfg.thinking_level == "high"
    assert cfg.get_model_for_tier(ModelTier.FLASH) == "gemini-3.7-flash"
    assert cfg.get_model_for_tier(ModelTier.PRO) == "gemini-3.1-pro-preview"


def test_config_dynamic_env_evaluation(monkeypatch):
    monkeypatch.setenv("MANTIS_PRO_MODEL", "gemini-custom-pro")
    monkeypatch.setenv("MANTIS_FLASH_MODEL", "gemini-custom-flash")
    monkeypatch.setenv("GOOGLE_CLOUD_PROJECT", "my-custom-project")
    monkeypatch.setenv("GOOGLE_CLOUD_REGION", "us-east4")

    cfg = MantisConfig()
    assert cfg.pro_model == "gemini-custom-pro"
    assert cfg.flash_model == "gemini-custom-flash"
    assert cfg.default_gcp_project == "my-custom-project"
    assert cfg.default_gcp_location == "us-east4"


def test_config_project_precedence(monkeypatch):
    # GOOGLE_CLOUD_PROJECT takes precedence over GCP_PROJECT
    monkeypatch.setenv("GCP_PROJECT", "secondary-project")
    monkeypatch.setenv("GOOGLE_CLOUD_PROJECT", "primary-project")
    cfg = MantisConfig()
    assert cfg.default_gcp_project == "primary-project"

    monkeypatch.delenv("GOOGLE_CLOUD_PROJECT")
    cfg2 = MantisConfig()
    assert cfg2.default_gcp_project == "secondary-project"


def test_config_provider_resolution(monkeypatch):
    # 1. Programmatic override
    cfg = MantisConfig(provider_override=ProviderType.AI_STUDIO)
    assert cfg.provider == ProviderType.AI_STUDIO

    # 2. Offline mode
    cfg_auto = MantisConfig()
    monkeypatch.setenv("MANTIS_OFFLINE", "true")
    assert cfg_auto.provider == ProviderType.OFFLINE_DETERMINISTIC

    # 3. AI Studio via GEMINI_API_KEY
    monkeypatch.delenv("MANTIS_OFFLINE")
    monkeypatch.setenv("GEMINI_API_KEY", "fake-key")
    assert cfg_auto.provider == ProviderType.AI_STUDIO

    # 4. Default to Vertex AI
    monkeypatch.delenv("GEMINI_API_KEY")
    assert cfg_auto.provider == ProviderType.VERTEX_AI
