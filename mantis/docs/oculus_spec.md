# 🦗 Oculus of Triage: Mantis Oculus Technical Spec

This document serves as the technical specification and engineering source of truth for **Mantis Oculus** (the AI-powered diagnostic triage agent of Project Mantis).

---

## 1. Goal & Overview

When **Mantis Raptorial Claws** detects an actual test failure (either locally or on GitHub CI), **Mantis Oculus** is called to dissect, correlate, and diagnose the failure. 

Oculus acts as a ruthless AI predator of bugs, analyzing execution traces to answer three core developer questions:
1. **Did the Device (Pubber) work as expected?**
2. **Did the Sequencer work as expected?**
3. **Did the Backend (UDMIS) work as expected?**

It correlates sharded/global logs using transaction IDs and timestamps, compares failed executions against past successful reference runs, and outputs an evidence-backed diagnostic report.

---

## 2. Log Correlation Architecture

Oculus reconstructs the timeline of a failed test case by correlating three primary logs:

```
                  +------------------------+
                  |      Sequencer         |
                  |   (sequence.log)       |
                  +-----------+------------+
                              |
                Session Base Transaction ID
                e.g. "RC:a2f5c3" & Timestamp
                              |
             +----------------+----------------+
             |                                 |
+------------v-----------+        +------------v-----------+
|       Pubber           |        |       UDMIS            |
|     (pubber.log)       |        |      (udmis.log)       |
+------------------------+        +------------------------+
```

### 2.1. Key Correlation Identifiers:
1. **Base Session Transaction ID (`RC:XXXXXX`)**:
   Every sequencer session generates a unique base transaction ID (e.g. `RC:a2f5c3`) when setting up the initial device config. This ID is the primary key for tracing configs, state updates, and reflection processing.
   - **Pubber Logs**: Logs the receipt and application of configs tagged with transaction IDs.
   - **UDMIS Logs**: Logs reflections and processing streams with the base ID and incrementing hex suffixes (e.g., `RC:a2f5c3.0000001b`).
2. **Timestamps**:
   Using the starting and ending timestamps of the test case from the sequencer's localized log, Oculus establishes a **Time Window** to filter entries out of global interleaving log streams.

---

## 3. Context Discovery & Reference Comparison

To diagnose failures with high accuracy, Oculus dynamically collects:
1. **Failed Execution Context**:
   - Local Test Trace (`sequence.md` & `sequence.log` inside `sites/udmi_site_model/out/devices/<device>/tests/<test_name>/`).
   - Correlated entries matching `RC:XXXXXX` from `pubber.log` and `udmis.log` within the test's time window.
   - Test Sequence Source Code: Dynamically parses `*Sequences.java` to extract the exact Java method of the executed test.
2. **Successful Reference Context**:
   - Compares the failed execution sequence against a **Past Successful Run** (automatically discovered from a preceding iteration in `measure`'s metrics or from the golden files in `etc/`).
   - Pinpoints the exact event/message index where the failed run deviated from the successful run.

---

## 4. Triage Diagnostic Objectives

Oculus evaluates the correlated timeline against the standard **UDMI Config-State Loop**:
1. **Sequencer** publishes config update $\rightarrow$ 2. **UDMIS** routes config $\rightarrow$ 3. **Pubber** applies config and publishes state $\rightarrow$ 4. **Sequencer** receives state and validates expectations.

It isolates the **Breakpoint** using the following heuristics:
- **Device Fault**: Did Pubber receive the config? If yes, did it throw an exception, delay writing, or publish incorrect state?
- **Sequencer Fault**: Did the Sequencer start validation timers too early, fail to wait for propagation delays, or assert incorrect values?
- **Backend Fault**: Did UDMIS drop the message, fail to reflect, or inject malformed envelopes?
- **Network Fault**: Did latency exceed wait thresholds?

---

## 5. CLI & CI Integration Specifications

### 5.1. Standalone CLI Usage
Developers can run Oculus directly on any failed iteration:

```bash
mantis/bin/triage \
  --run-dir <path_to_iteration_backup> \
  --test <test_name>
```

### 5.2. GitHub CI Integration
Oculus is designed to run natively in GitHub Actions:
1. **Post-Test step**: In `.github/workflows/testing.yml`, a dedicated `triage` job is triggered if `posttest` detects a failure.
2. **Automatic Trigger**: Ingests the consolidated `udmi-support_${{ github.run_id }}` package, scans for failures, and runs Oculus on each.
3. **PR Reporting**:
   - Saves a consolidated `mantis_triage_report.md` as a build artifact.
   - Automatically comments the diagnostic evidence directly onto the triggering Pull Request or Commit!

---

## 6. Prompt Design & LLM Specifications

- **SDK Package**: Standard `google.genai` library (unbuffered, low-latency).
- **Authentication**: `GEMINI_API_KEY` environment variable.
- **Model**: `gemini-2.5-pro` (high logical reasoning and large system log understanding).
- **Output format**: Generates a localized `triage_analysis.md` containing:
  - **Breakpoint Summary**: "Failed at Step 4: Sequencer timed out waiting for point value status change."
  - **Correlated Evidence Table**: Time-aligned logs showing Sequencer, UDMIS, and Pubber statements leading to the failure.
  - **Divergence Analysis**: "Run 4 diverged from Successful Run 1 at 14:15:10Z: Device did not publish State update after config apply."
  - **Root Cause & Propose Fix**: Step-by-step correction plan.
