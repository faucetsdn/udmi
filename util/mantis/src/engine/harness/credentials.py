import os
import subprocess
import sys
from abc import ABC, abstractmethod
from typing import Any, Optional
from google import genai


class BaseCredentialsProvider(ABC):
    """Abstract base class for custom authentication / credentials resolvers."""

    @abstractmethod
    def get_client(self) -> genai.Client:
        """Resolves credentials and returns an authenticated GenAI Client instance."""
        pass


class EnvCredentialsProvider(BaseCredentialsProvider):
    """
    Credentials provider that resolves GenAI clients using:
    1. Google Cloud Vertex AI (via Application Default Credentials or gcloud SDK config)
    2. Direct Gemini Developer API key (GEMINI_API_KEY environment variable)
    """

    def __init__(
        self,
        use_vertex: Optional[bool] = None,
        project: Optional[str] = None,
        location: Optional[str] = None
    ):
        self.use_vertex = use_vertex if use_vertex is not None else (
            os.getenv("MANTIS_USE_VERTEXAI", "").lower() in ("true", "1", "yes")
        )
        self.project = project
        self.location = location

    def get_client(self) -> genai.Client:
        """Initializes and returns the authenticated genai.Client."""
        api_key = os.getenv("GEMINI_API_KEY")
        if not self.use_vertex and api_key:
            return genai.Client()


        # Fallback to Google Cloud Vertex AI (ADC)
        project_id = self.project or os.getenv("GCP_PROJECT") or os.getenv("GCLOUD_PROJECT")
        if not project_id:
            try:
                res = subprocess.check_output(["gcloud", "config", "get-value", "project"], text=True, stderr=subprocess.DEVNULL)
                if res.strip():
                    project_id = res.strip()
            except Exception:
                pass
        if not project_id:
            project_id = "bos-platform-dev"


        location = self.location or os.getenv("GCP_LOCATION", "global")
        print(f"[Vertex AI] Initializing Client (project: {project_id or 'ADC Default'}, location: {location})...")
        try:
            if project_id:
                return genai.Client(vertexai=True, project=project_id, location=location)
            else:
                return genai.Client(vertexai=True, location=location)
        except Exception as e:
            if not api_key and not self.use_vertex:
                raise RuntimeError(
                    f"Authentication Failed: GEMINI_API_KEY is not set, and Vertex AI initialization failed: {e}. "
                    "Please set GEMINI_API_KEY or configure Vertex AI / gcloud auth."
                )
            raise RuntimeError(f"Vertex AI initialization error: {e}")

