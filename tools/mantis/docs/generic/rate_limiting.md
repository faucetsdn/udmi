# Outbound Rate Limiting & Fail-Open Timeout Standard

To safeguard downstream Gemini API and Vertex AI endpoints from denial-of-service, quota exhausts (429), or starvation, Mantis integrates a token-bucket rate limiter and a **Non-Blocking Fail-Open Timeout** strategy.

---

## 1. Playbook Rate Limits Configuration

Rate limits are configured globally inside the playbook yaml's `pipeline_config` block:

```yaml
metadata:
  name: "Adwords Triage Playbook"

pipeline_config:
  default_model: "gemini-3.5-pro"
  # Max request tokens allowed per minute across all active execution threads
  max_queries_per_minute: 15
  # Max loop turns per single stage (safety guardrail)
  max_loops: 15
```

* **`max_queries_per_minute`** (default: `15`): Dictates the token-bucket size and refill speed.
* If multiple concurrent triage sessions are active (e.g. CI/CD runners analyzing multiple test failure shards), they share a single thread-safe global `AsyncRateLimiter` to avoid hitting upstream tenant quotas.

---

## 2. Fail-Open Timeout Control

During high congestion or quota exhaustion, waiting to acquire a rate-limiting token might stall the CI/CD pipeline. Mantis enforces a strict **Fail-Open Policy**:

1. **Acquisition Timeout**: A request has a maximum of **45 seconds** to acquire a rate-limiting token.
2. **Crash Prevention**: If the timeout expires before a token becomes available, the pipeline raises `RateLimitTimeoutError` which is caught immediately at the pipeline execution layer.
3. **Partial Report Compilation**: The orchestrator harvests all diagnostics gathered so far (e.g. deterministic timeline steps, code searches completed), and compiles a fallback markdown summary.
4. **Clean Exit Code**: Rather than returning an error status code and halting the deployment pipeline, Mantis saves the partial report to the output directory, logs a warning on `stderr`, and **exits successfully with status code `0`**.

---

## 3. CI/CD Fail-Open Setup Example

No special pipeline guards are needed since Mantis handles fail-open timeouts natively. A standard shell call:

```bash
# Run triage in CI/CD pipeline
bin/triage --target_id="test_run_123" --workspace_root="."
# Exit code is guaranteed to be 0 even if Gemini quotas are completely exhausted!
echo "Exit status: $?"
```
