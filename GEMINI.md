[**UDMI**](./) / [Agent Instructions](#)

# UDMI Project Instructions

## Engineering Standards for Triage and Verification

To ensure technical integrity in this multi-component system (comprising Python, Java, and various middleware), all bug fixes must adhere to the following principles:

### 1. Empirical Failure Reproduction
- **Principle**: Do not rely on high-level test results (e.g., "Tests Passed") as the sole indicator of success.
- **Mandate**: You must identify and reproduce the **internal technical signature** of the failure (e.g., specific log patterns, unexpected null fields, or erroneous state entries) as reported in the failing environment (CI logs, user reports).
- **Verification**: A fix is only valid if you can demonstrate the specific removal or correction of this internal signature in a controlled run.

### 2. Negative Verification (Reversion Testing)
- **Principle**: Ensure the fix is the direct cause of the resolution.
- **Mandate**: Once a fix is verified as "passing," you must temporarily revert the change and re-run the reproduction case.
- **The Negative-Pass Hard Stop**: If the test **passes** after the fix is reverted, the environment is contaminated. You MUST NOT proceed. You must stop, declare a "Sanitization Failure" in a topic update, and backtrack until the failure is reproduced.

### 3. Log-Based Evidence of Transition
- **Principle**: Provide transparent proof of behavioral change.
- **Mandate**: Final verification summaries must include raw log snippets showing the **before-and-after state** of the system's internal logic. This transition is the only acceptable proof of a successful repair.

### 4. Boundary Data Probing
- **Principle**: Verify data integrity at the points of hand-off between languages or services.
- **Mandate**: When issues involve data serialization or cross-service communication, you must probe the raw data at the boundary (e.g., inspecting raw MQTT payloads, database state, or using instrumentation logs) to confirm that mapping logic (like JSON-to-Object) is operating correctly.

### 5. State Isolation and Sanitization
- **Principle**: Prevent cross-contamination between test runs.
- **Mandate**: Before performing final verification, you must ensure all persistent state (Docker volumes, cached credentials, database entries, and temporary files) is explicitly cleared.
- **Proof of Failure**: Before any verification run, you must provide log evidence that the failure is **currently active** in the environment. A fix is only valid if applied to a demonstrably broken state.
- **Warning**: If permissions (e.g., `sudo`) prevent full sanitization, the environment must be treated as "Untrusted" and verification cannot be considered conclusive.
