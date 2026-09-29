"""Centralized model constants, provider configuration, and runtime settings for Mantis."""

import os
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional


class ModelTier(str, Enum):
    FLASH = "flash"
    PRO = "pro"


class ProviderType(str, Enum):
    VERTEX_AI = "vertex_ai"
    AI_STUDIO = "ai_studio"
    OFFLINE_DETERMINISTIC = "offline_deterministic"


@dataclass
class MantisConfig:
    # Model Names
    flash_model: str = field(default_factory=lambda: os.getenv("MANTIS_FLASH_MODEL", "gemini-3.7-flash"))
    pro_model: str = field(default_factory=lambda: os.getenv("MANTIS_PRO_MODEL", "gemini-3.1-pro-preview"))

    # Reasoning depth for the deep-reasoning tier. High thinking is the default:
    # against gemini-3.1-pro-preview it reached a conclusion in 19 tool calls
    # instead of 42, issued no duplicate searches, and was the only configuration
    # that read backend (udmis/) source rather than only grepping it.
    thinking_level: Optional[str] = field(default_factory=lambda: os.getenv("MANTIS_THINKING_LEVEL", "high"))

    # Vertex AI Defaults
    default_gcp_project: Optional[str] = field(default_factory=lambda: os.getenv("GOOGLE_CLOUD_PROJECT", os.getenv("GCP_PROJECT")))
    default_gcp_location: str = field(default_factory=lambda: os.getenv("GOOGLE_CLOUD_REGION", os.getenv("GCP_REGION", "global")))

    # Timeouts & Limits
    stack_startup_timeout_sec: int = field(default_factory=lambda: int(os.getenv("MANTIS_STARTUP_TIMEOUT_SEC", "90")))
    test_wait_timeout_sec: int = field(default_factory=lambda: int(os.getenv("MANTIS_TEST_TIMEOUT_SEC", "120")))
    max_log_lines: int = field(default_factory=lambda: int(os.getenv("MANTIS_MAX_LOG_LINES", "200")))

    # Base Port Allocation Settings
    port_base_min: int = 20000
    port_base_max: int = 55000
    port_block_size: int = 10

    # Optional programmatic provider override
    provider_override: Optional[ProviderType] = None

    # Provider Resolution
    @property
    def provider(self) -> ProviderType:
        if self.provider_override is not None:
            return self.provider_override

        # 1. Explicit offline mode check
        if os.getenv("MANTIS_OFFLINE", "").lower() in ("true", "1", "yes"):
            return ProviderType.OFFLINE_DETERMINISTIC

        # 2. Google AI Studio API key check
        if os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY"):
            return ProviderType.AI_STUDIO

        # 3. Default: Vertex AI in the project from GOOGLE_CLOUD_PROJECT / GCP_PROJECT
        return ProviderType.VERTEX_AI

    def get_model_for_tier(self, tier: ModelTier) -> str:
        if tier == ModelTier.FLASH:
            return self.flash_model
        return self.pro_model


# Global configuration instance
CONFIG = MantisConfig()
