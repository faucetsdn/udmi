# 🦗 Claws of Stability: Mantis Grasp Technical Spec

This document serves as the technical specification and engineering source of truth for **Mantis Grasp** (the pure metric and stability analyzer of Project Mantis).

---

## 1. Goal & Overview

Following strict **Single Responsibility Design (SRD)**, **Mantis Grasp** is decoupled from test execution, environment startup, MOSQUITTO port checks, validator compilations, loop counts, and process cleanups. 

All environment triggers and run executions (both on GitHub CI and local developer sandboxes) are delegated entirely to the **Capture** component. Grasp's sole responsibility is to ingest captured support packages, normalize result outputs, aggregate flakiness metrics, and output MD reports.

---

## 2. Architecture & System Design

```mermaid
graph TD
    subgraph Component A: GitHub & Local Capture (Execution Engine)
        Capture[capture CLI] -->|Local execution or CI trigger| Exec[Run test loops & clean processes]
        Exec -->|Zip raw outcomes| BundlesDir[out/mantis/test_bundles/target_timestamp/]
    end

    subgraph Component B: Grasp Metric Analyzer (Pure Analysis)
        Grasp[grasp CLI] -->|1. Read zip/tgz bundles| BundlesDir
        Grasp -->|2. Extract & Consolidate| Support[bin/support_process]
        Support -->|3. Read sequencer.out & test_itemized.out| Analyzer[Log Analyzer]
        Analyzer -->|4. Normalize & Match Golden| Aggregator[Metrics Aggregator]
        Aggregator -->|5. Serialize metrics.json| Reporter[Report Generator]
        Reporter -->|6. Generate MD & Comparative reports| Markdown[Stability Reports]
    end
```

---

## 3. Core Algorithmic Rules

### 3.1. Actual Failures vs. Intended Failures
In the UDMI test suite, some failure scenarios are part of the expected test design. To accurately classify stability, Mantis compares run results directly to **Golden Baselines** (`etc/sequencer.out` and `etc/test_itemized.out`):

- **Normalizations**:
  Before doing any comparison, Mantis applies regex-based normalizations:
  - Replaces variable ISO timestamps (`202[-0-9T:]+Z`) with `'TIMESTAMP'`.
  - Redacts variable error details in pipeline errors (`Pipeline type event error: While processing message .*`) keeping only the prefix and `REDACTED`.
- **Sequential Occurrence-Based Matching**:
  Because the same test case can run multiple times under different parameters, Mantis keeps track of the occurrence index of each test. The $n$-th occurrence of a test in a run is compared exactly to the $n$-th occurrence of that test in the golden baseline.
- **Pass vs Actual Fail**:
  - If the normalized result matches the corresponding normalized baseline result (even if the outcome is `fail` or `skip`), the test is marked as **Pass (Expected Behavior)**.
  - If there is any mismatch or a test is missing, it is marked as an **Actual Failure (Instability / Regression)**.

---

## 4. Submodule Directory Structure & Naming

The Grasp component is implemented under the `mantis/grasp/` submodule:

```
mantis/grasp/
├── __init__.py
├── main.py               # Pure log aggregator & consolidated metric orchestrator
├── analyzer.py           # Parses test output results & matches baseline
└── reporter.py           # Computes metrics & renders Markdown reports
```

---

## 5. Implementation Specifications

### 5.1. CLI Wrapper (`bin/grasp`)
Launches the module: `python3 -m mantis.grasp.main "$@"`

### 5.2. Input Arguments
| Argument | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `--bundles-dir` | `string` | **Required** | Path to folder containing sharded/local test bundles (zips/tgz). |
| `--target` | `string` | `//mqtt/localhost` | The target project specification under test. |
| `--phase` | `"before"` or `"after"` | `"before"` | Exercise stage (used for reports). |
| `--output-dir` | `string` | `out/mantis` | Folder where all reports are saved. |

---

## 6. Verification & Smoke Test Details

1. **Argument Parsing Help Check**:
   - Command `mantis/bin/grasp --help` executes cleanly, verifying imports, argument types, and required fields.
2. **Decoupled Integration Test**:
   - Verify that Grasp accurately reads a directory of mock bundles, consolidates their contents, and outputs `flakiness_report_<phase>_<target>.md` without requiring local environments.
