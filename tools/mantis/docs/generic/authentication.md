# Mantis Authentication & Credentials Setup Guide

Mantis decouples GenAI clients from static api key variables by introducing `CredentialsProvider` interfaces. This enables developers to run diagnostics on local workstations using user identities, and automatically cascade to robot credentials in automated CI/CD pipelines.

---

## 1. Local Workstation Setup (API Key)

For local execution, Mantis uses the Google GenAI SDK. Obtain a Gemini API Key from Google AI Studio and configure it:

1. **Set Environment Variable**:
   ```bash
   export GEMINI_API_KEY="AIzaSy..."
   ```

2. **Verify Execution**:
   Run a diagnostics sweep:
   ```bash
   bin/diagnose --target_id="local_debug"
   ```

---

## 2. CI/CD & Enterprise Setup (Vertex AI)

In enterprise, monorepos, and CI/CD pipelines (e.g. GitHub Actions, Tekton, or cloud build pipelines), developers should avoid embedding long-lived API Keys. Instead, use Vertex AI authentication:

1. **Enable Vertex AI Integration**:
   Set `MANTIS_USE_VERTEXAI` to `true`:
   ```bash
   export MANTIS_USE_VERTEXAI="true"
   ```

2. **GCP Project Scoping**:
   Mantis automatically resolves active GCP project settings from standard variables:
   ```bash
   export GCP_PROJECT="your-gcp-project-id"
   export GCP_LOCATION="us-central1"
   ```

3. **Application Default Credentials (ADC)**:
   Ensure that the runner has active Application Default Credentials. 
   * **Workstation / local testing**: Run `gcloud auth application-default login`.
   * **Google Cloud CI (GKE/Cloud Build)**: Bind the Workload Identity / Service Account to the runner process. The SDK will authenticate automatically.
