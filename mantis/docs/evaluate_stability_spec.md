# 🦗 Claws of Stability: Mantis Evaluate Stability Technical Spec

This document serves as the technical specification and engineering source of truth for **Mantis Evaluate Stability** (the pure metric and stability analyzer of Project Mantis, Stage 2).

---

## 1. Goal & Overview

Following strict **Single Responsibility Design (SRD)**, **Mantis Evaluate Stability** is decoupled from test execution, environment startup, broker checks, validator compilations, and active sandbox runs. 

All environment triggers and run executions (both on GitHub CI and local developer sandboxes) are delegated entirely to the **Statistics Collector** (`collect_stats`) component. The Stability Evaluator's sole responsibility is to ingest captured support packages, normalize result outputs, aggregate flakiness metrics, and output Markdown and comparative stability reports under `mantis/out/`.

---

## 2. Architecture & System Design

```mermaid
graph TD
    subgraph Component A: Statistics Collector (Execution Engine)
        Collector[collect_stats CLI] -->|Local execution or CI trigger/search| Exec[Run test loops & gather packages]
        Exec -->|Save bundles| BundlesDir[mantis/out/test_bundles/target_timestamp/]
    end

    subgraph Component B: Stability Evaluator (Pure Analysis)
        Evaluator[evaluate_stability CLI] -->|1. Read zip/tgz bundles| BundlesDir
        Evaluator -->|2. Extract & Consolidate| Support[bin/support_process]
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
  - Replaces variable ISO timestamps (`202[-0-9T:]+Z` and variations) with `'TIMESTAMP'`.
  - Redacts variable error details in pipeline errors (`Pipeline type event error: While processing message .*`) keeping only the prefix and `REDACTED`.
- **Sequential Occurrence-Based Matching**:
  Because the same test case can run multiple times under different parameters, Mantis keeps track of the occurrence index of each test. The $n$-th occurrence of a test in a run is compared exactly to the $n$-th occurrence of that test in the golden baseline.
- **Pass vs Actual Fail**:
  - If the normalized result matches the corresponding normalized baseline result (even if the outcome is `fail` or `skip`), the test is marked as **Pass (Expected Behavior)**.
  - If there is any mismatch or a test is missing, it is marked as an **Actual Failure (Instability / Regression)**.

---

## 4. Submodule Directory Structure & Naming

The Stability Evaluator component is implemented under the `mantis/evaluate_stability/` submodule:

```
mantis/evaluate_stability/
├── __init__.py
├── main.py               # Stability evaluator coordinator & orchestrator
├── analyzer.py           # Parses test output results & matches baselines
└── reporter.py           # Computes metrics & renders Markdown reports
```

---

## 5. Implementation Specifications

### 5.1. CLI Wrapper (`mantis/bin/evaluate_stability`)
Launches the module: `python3 -m mantis.evaluate_stability.main "$@"`

### 5.2. Input Arguments
| Argument | Short Flag | Type | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `--bundles-dir` | `-i` | `string` | **Required** | Path to folder containing sharded/local test bundles (zips/tgz) or run subdirs. |
| `--compare` | `-c` | `list[str]` | `None` | Additional bundle directories to evaluate and compare chronologically. |
| `--target` | `-t` | `string` | `None` | Target project specification under test (auto-detected if omitted). |

### 5.3. Output & Comparative Reporting
- **Single Run**: Outputs metrics JSON and `flakiness_report_{clean_target}_{timestamp}.md` under `mantis/out/`.
- **Multiple Runs (Comparison)**: If `--compare` is provided, Mantis evaluates all checkpoints, resolves their run/creation timestamps chronologically, and outputs a comprehensive chronological comparative evolution report (`stability_comparison_{clean_target}.md`) showing overall progress and test case stabilization deltas (e.g., `🏆 Stabilized!`, `📈 Improved`, `🚨 Regressed`).

---

## 6. Verification & Smoke Test Details

1. **Argument Parsing Help Check**:
   - Command `mantis/bin/evaluate_stability --help` executes cleanly, verifying imports, argument types, and required fields.
2. **Chronological Comparative Evaluation**:
   - Verify that running `mantis/bin/evaluate_stability -i <dir_1> -c <dir_2>` successfully orders datasets, analyzes results, and renders the complete chronological comparison matrix.
