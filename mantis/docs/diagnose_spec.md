# 🦗 Oculus of Triage: Mantis Diagnose Technical Spec

This document serves as the technical specification and engineering source of truth for **Mantis Diagnose** (the AI-powered diagnostic triage agent of Project Mantis, Stage 3).

---

## 1. Goal & Overview

When **Mantis Evaluate Stability** aggregates sharded run metrics and detects actual test case failures (regressions/flakiness), **Mantis Diagnose** is invoked to isolate the defect.

It operates as a tool-equipped AI agent that dynamically discovers log context, correlates distributed streams, and analyzes execution traces to resolve three core questions:
1. **Did the Device under Test work as expected?**
2. **Did the Sequencer work as expected?**
3. **Did the UDMIS backend work as expected?**

Diagnose is completely **log-agnostic** and **highly adaptive**—automatically discovering whatever data streams are available, slicing them cleanly using time bounds, and executing playbook pipelines sequentially.

---

## 2. Dynamic Log-Agnostic Correlation Engine

Diagnose does not rely on rigid, pre-defined log combinations or expect the developer to select a specific diagnostic mode. Depending on the target platform and logging configurations, log streams (Sequencer, UDMIS, Pubber) might be present or completely absent.

### 2.1. Available Context Discovery
Before starting any analysis, Diagnose automatically scans the sharded run folder and compiles a live **Available Context Catalog** (logs present vs. missing). It passes this catalog directly to the GenAI model:
> *"Analyze this failure. Here is the exact set of logs currently available for this run, alongside the logs retrieved from past successful runs. Reconstruct the execution timeline using whatever combination of streams is present, perform a best-effort triage, and isolate the breakpoint with evidence."*

### 2.2. Padded Timebound-Primary Correlation
Rather than strictly filtering by transaction IDs, Diagnose uses **padded execution timebounds** as the primary log correlation mechanism:
- Scans the local sequencer log to locate the start and end timestamps of the target test case.
- Adds a slack padding (default: $5$ seconds before start, $5$ seconds after end).
- Slices all global log entries (`pubber.log`, `udmis.log`) within this time window.
- This guarantees that **surrounding system context** (e.g. broker updates, connection flips) is captured and analyzed alongside transaction-specific entries.

---

## 3. Submodule Directory Structure & Naming

The Triage/Diagnose component is implemented under the dedicated `mantis/src/triage/` directory, separating the reusable orchestrator harness from the UDMI implementation:

```
mantis/src/triage/
├── harness/              # Decoupled Generic AI Triage Harness
│   ├── config/
│   │   ├── cache.py      # Vectorized semantic cache engine
│   │   └── playbook.py   # Playbook configuration schema parser
│   ├── engine.py         # Async triage loop execution engine
│   └── pipeline.py       # Dynamic multi-stage pipeline orchestrator
└── impl/                 # UDMI-Specific Implementations
    ├── README.md
    ├── main.py           # Main CLI entrypoint and launch coordinator
    ├── cli.py            # Argument parser configurations
    ├── runner.py         # Parallel failures executor & log slicer
    ├── agent.py          # AI agent harness and Gemini API clients
    ├── resolver.py       # Sharded logs resolver and path consolidator
    ├── playbook.yaml     # Declarative UDMI diagnostics playbook
    ├── tools.py          # Exposes codebase, git, and log tools
    └── skills/           # SKILL.md files with localized AI prompts
```

* **`harness/`**: The generic framework. Executes sequential playbook stages, handles tool calling loops, and manages the vectorized semantic caching.
* **`impl/runner.py`**: Coordinates the parallel multi-job async diagnostics run. It resolves sharded run directories and matches failed execution shards against successful baseline shards.
* **`impl/agent.py`**: Orchestrates GenAI API calls, executes playbook stages sequentially (e.g., Timeline, Intent, Analysis, Critique), and manages semantic caching.
* **`impl/playbook.yaml`**: Declares the custom pipeline stages, model specifications, concurrency limits, and system instructions shaping the triage strategy.

---

## 4. Playbook Execution Stages & Intermediate Outputs

Mantis Diagnose operates via a structured **playbook execution pipeline** to ensure maximum diagnostic depth. Each enabled playbook stage runs sequentially, feeding its outcomes as cumulative context to the next stage:

1. **Timeline Stage** 🕒: A Timeline Harvester builds an exhaustive chronological timeline of events across Sequencer, UDMIS, and Pubber/Device logs. Saves outcome as `stage_timeline.md`.
2. **Intent Stage** 🎯: A Test Intent Harvester scans references/schemas to locate and summarize static test design intent and baseline sequence expectations. Saves outcome as `stage_intent.md`.
3. **Analysis Stage** 🔍: A Defect Triage Analyst traces codebase implementations, audits concurrent message listener queues, and isolates the root cause code flaw. Saves outcome as `stage_analysis.md`.
4. **Critique Stage** ⚖️: A Peer Critique Reviewer checks the proposed Root Cause Analysis (RCA) against design intent and timestamps, rejecting weak or incomplete findings. Saves outcome as `stage_critique.md`.

### Vectorized Semantic Cache
A centralized `semantic_cache.json` stores past successful diagnostics. On cache hits (high cosine similarity), Mantis delivers zero-shot diagnostic reports in milliseconds, skipping playbook pipeline calls.

---

## 5. Zero-Configuration CLI & Scoping

Diagnose utilizes a smart CLI entrypoint, completely eliminating manual target, site model, and device ID specifications.

### 5.1. CLI Wrapper (`mantis/bin/diagnose`)
Launches the module: `python3 -m mantis.src.triage.impl.main "$@"`

### 5.2. Authentication & Enterprise Quotas
Mantis supports dual authentication strategies for AI executions:
* **Developer API Key**: Set `export GEMINI_API_KEY="your_key"` to connect to the standard public endpoint (subject to free-tier rate limits).
* **Enterprise Google Cloud Vertex AI**: Set `export MANTIS_USE_VERTEXAI=true` to route all calls through Vertex AI using GCP Application Default Credentials (ADC). This leverages GCP project allocations and provides high enterprise quotas (practically eliminating 429 errors when run within Google's environment).
  * Optional environment parameters: `GCP_PROJECT` (overrides GCP target project ID) and `GCP_LOCATION` (default: `us-central1`).

### 5.3. Input Arguments
| Argument | Short Flag | Type | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `--manifest` | `-m` | `string` | `None` | Path to `triage_manifest.json` compiled during Stage 2 stability evaluation. Activates Manifest Mode. |
| `--bundles-dir` | `-i` | `string` | `None` | Path to input bundles directory (scans folders for failures if manifest is omitted). |
* Overrides / standalone flags:
  * `--test` / `-t` (list of tests to target), `--device` / `-d` (list of devices to filter).
  * `--sequence-log` / `-sl` (direct path to sequencer log), `--device-log` / `-dl` / `--udmis-log` / `-ul` / `--success-log` / `-scl` (direct log overrides).
  * `--concurrency` / `-c` (maximum parallel triage runs, default: `3`).

---

## 6. Structured Output Specifications

### 6.1. Bundle Self-Containment Redirection
All outputs are written under the active execution directory (the parent directory of the manifest file) for absolute self-containment and absolute modularity:

```
out/mantis/test_bundles/<bundle_name>/
├── triage_manifest.json
├── diagnose.log             # Complete terminal execution logs
└── diagnose/
    └── <project_id>/
        └── <site_id>/
            ├── triage_summary_report.md  # Consolidated site-level metrics & signatures
            └── <device_id>/
                └── <test_id>/
                    ├── stage_timeline.md  # Intermediate Timeline Stage output
                    ├── stage_intent.md    # Intermediate Intent Stage output
                    ├── stage_analysis.md  # Intermediate Analysis Stage output
                    ├── stage_critique.md  # Intermediate Critique Stage output
                    └── triage_analysis.md # Unified final report and proposed code fixes
```

### 6.2. Structured Premium Triage Analysis (`triage_analysis.md`)
Localized reports conform to the structured markdown format parsed by the summary reporter:
* **Executive Defect Summary**: A single-line technical blockquote summary (`> [Summary]`) detailing the component and exact failure, followed by technical details.
* **Component Assessment**: Operator operational status (Device, Sequencer, UDMIS) showing Operational Status (Yes/No/Partial) with concise evidence.
* **Surgical Timeline Evidence**: Highly condensed chronological table (5-7 rows) proving the deviation/missed event.
* **Root Cause Tracing**: Failing class/component name and vulnerable logic.
* **Resilience Recommendation**: Actionable structural suggestions accompanied by a unified source code diff block if applicable.
