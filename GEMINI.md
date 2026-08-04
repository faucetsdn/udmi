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
- **Warning**: If permissions (e.g., `sudo`) prevent full sanitization, the environment must be treated as "Untrusted" and verification cannot be considered conclusive. Note that when running local services in unprivileged environments, specifying an explicit port number in the project spec (e.g., `//mqtt/localhost:18833`) triggers automatic isolated mode without requiring sudo.

### 6. Staged Final Verification (Project-Wide Integrity)
To prevent regressions in this reflectively-coupled system, verification MUST proceed in two distinct stages. You may only proceed to Stage 2 if Stage 1 passes completely.

#### Stage 1: Unit & Static Integration Integrity (Fast/Offline)
- **Goal**: Catch compilation errors, logic regressions, schema & reflective mapping errors (`classForSchema`), generated code serialization, trace replay regressions, site model diffs, and utility regressions.
- **Mandate**: Run the comprehensive unit test suite covering code, schemas, traces, registrar, and utilities.
- **Command**: `bin/run_tests all_tests`
  * Runs `code_tests`, `schema_tests`, `trace_tests`, `registrar_tests`, and `util_tests`.

#### Stage 2: Functional Pipeline Integrity (Local Integration)
- **Goal**: Verify the end-to-end message pipeline (Validator -> Sequencer -> UDMIS) handles both standard and "unknown" cases without reflective failures.
- **Mandate**: Run the comprehensive local integration suite. This stage is non-negotiable for any change touching `common`, `gencode`, or message-processing logic.
- **Startup Timeout Hard Stop**: `bin/start_local` must be ready (`UUFI Service is READY`) within **90 seconds**. If local services are not ready after 90 seconds, treat it as an unrecoverable core system failure — stop execution immediately and report the failure. Do NOT attempt to diagnose, debug, or repair the environment.
- **Commands**:
  1. `bin/run_tests install_dependencies` (Ensure clean local environment)
  2. `bin/start_local sites/udmi_site_model //mqtt/localhost:46432` (Start local services; wait max 90s for UUFI Service is READY)
  3. `bin/test_special //mqtt/localhost:46432` (Special sequence integration validation)
  4. `bin/test_validator //mqtt/localhost:46432` (Telemetry validation)
  5. `bin/test_sequencer nostate full //mqtt/localhost:46432` (Exhaustive pipeline verification)
  6. `bin/test_runlocal` (UDMIS component verification)

### 7. Golden Expectation Integrity (Anti-Cheating Rule)
- **Principle**: Golden test files (such as `etc/validator.out` or `etc/schema_nostate.out`) define expected system outputs and baseline test coverage.
- **Mandate**: Updating golden files simply to force failing integration tests to pass (e.g., executing `cp out/validator.out etc/validator.out` without specific, audited technical rationale) is considered cheating and is strictly forbidden.
- **Rules**:
  1. Golden files may ONLY be updated when there is an intentional, documented system output change (such as an explicit schema or gencode version upgrade).
  2. Any diff to golden files must be line-by-line audited to ensure no expected test cases or event outputs (e.g., `events_blobset.out`, `events_discovery.out`, or `events_invalid.out`) were omitted or truncated due to test timing or incomplete pubber execution.

**Authoritative Source**: If in doubt, audit `.github/workflows/testing.yml` for the current set of `run:` commands. Declaring a task "DONE" without completing both stages is a violation of engineering standards.
