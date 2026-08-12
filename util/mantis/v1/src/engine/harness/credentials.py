import os
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
    Default credentials provider that resolves GenAI clients using environment
    variables (GEMINI_API_KEY) or Google Application Default Credentials (ADC) for Vertex AI.
    """

    def __init__(self, use_vertex: Optional[bool] = None):
        self.use_vertex = use_vertex if use_vertex is not None else (
            os.getenv("MANTIS_USE_VERTEXAI", "").lower() in ("true", "1", "yes")
        )

    def get_client(self) -> genai.Client:
        """Initializes and returns the authenticated genai.Client."""
        if self.use_vertex:
            project_id = os.getenv("GCP_PROJECT") or os.getenv("GCLOUD_PROJECT")
            location = os.getenv("GCP_LOCATION", "global")
            print(f"[Vertex AI] Resolving credentials via ADC (project: {project_id or 'Auto-detect'}, location: {location})...")
            try:
                return genai.Client(vertexai=True, project=project_id, location=location)
            except Exception as e:
                print(f"Error: Failed to initialize Vertex AI GenAI Client: {e}", file=sys.stderr)
                sys.exit(1)
        else:
            token = os.getenv("GEMINI_API_KEY")
            if not token:
                print("Error: GEMINI_API_KEY environment variable is not set and Vertex AI is disabled.", file=sys.stderr)
                sys.exit(1)
            return genai.Client()
