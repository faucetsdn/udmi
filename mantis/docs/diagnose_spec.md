# 🦗 Oculus of Triage: Mantis Diagnose Technical Spec

This document serves as the technical specification and engineering source of truth for **Mantis Diagnose** (the AI-powered diagnostic triage agent of Project Mantis, Stage 3).

---

## 1. Goal & Overview

When **Mantis Evaluate Stability** aggregates flakiness metrics and detects actual test failures, **Mantis Diagnose** is called to diagnose the issue.

It operates as a tool-equipped AI agent that dynamically discovers context and analyzes execution traces to answer three core questions:
1. **Did the Device work as expected?**
2. **Did the Sequencer work as expected?**
3. **Did the Backend (UDMIS) work as expected?**

Diagnose is built to be completely **log-agnostic** and **highly adaptive**—automatically discovering whatever data streams are available at hand, slicing them cleanly, and using git history to perform best-effort diagnostics.

---

## 2. Dynamic Log-Agnostic Correlation Engine

Diagnose does not rely on rigid, pre-defined log combinations or expect the developer to select a specific diagnostic mode. Any of the log sources (Sequencer, UDMIS, Pubber, or historical logs) might be present or completely absent depending on the target platform and logging configurations.

### 2.1. Available Context Discovery
Before starting any analysis, Diagnose automatically scans the execution backup directory and compiles a live **Available Context Catalog** (logs present vs. missing). It passes this catalog directly to the Gemini model, instructing the AI:
> *"Analyze this failure. Here is the exact set of logs currently available for this run, alongside the logs retrieved from past successful runs. Reconstruct the execution timeline using whatever combination of streams is present, perform a best-effort triage, and isolate the breakpoint with evidence."*

### 2.2. Padded Timebound-Primary Correlation
Rather than strictly filtering by transaction IDs, Diagnose uses **padded execution timebounds** as the primary log slicer:
- Finds the starting and ending timestamps of the test case from the sequencer log.
- Adds a slack padding (default: $-5$ seconds before start, $+5$ seconds after end).
- Slices all global log entries (`pubber.log`, `udmis.log`) within this window.
- This guarantees that **surrounding system context** (e.g. broker crashes, database locks) is captured and analyzed alongside transaction-specific entries.

---

## 3. Submodule Directory Structure & Naming

The Triage/Diagnose component is implemented under its dedicated submodule folder `mantis/diagnose/`:

```
mantis/diagnose/
├── __init__.py
├── main.py               # Triage coordinator & context aggregator
├── agent.py              # AI agent harness, SkillRegistry & Gemini API client
├── tools.py              # Dynamic tool belt (grep, read lines, git operations)
└── skills/               # Conforming folders containing SKILL.md files
    ├── progressive-triage-flow/SKILL.md
    ├── component-guide/SKILL.md
    ├── log-sources/SKILL.md
    ├── log-correlation/SKILL.md
    ├── evidence-gathering/SKILL.md
    └── best-effort-triage/SKILL.md
```

- **`main.py`**: Orchestrates the execution flow, maps folder trees, and outputs report documents under `mantis/out/diagnose/`.
- **`agent.py`**: Manages standard `google.genai` SDK sessions, batch-registers the skills folder via the official **Agent Skills SDK** (`agentskills-core` and `agentskills-fs`), and queries the GenAI model with dynamic progressive disclosure.
- **`tools.py`**: Defines Python execution methods exposed directly to Gemini.

---

## 4. Agentic Codebase & Git Discovery (Tools)

Diagnose is provided with a "Tool Belt" of Python-based functions that it can call dynamically during its diagnostic analysis loop:

### 4.1. Tool Belt APIs (defined in `tools.py`):
1. **`grep_codebase(pattern)`**:
   - Executes ripgrep/grep across the repository (`validator/`, `udmis/`, `pubber/`) matching the search pattern.
2. **`read_file_lines(filepath, start_line, end_line)`**:
   - Reads a contiguous chunk of lines from a file in the workspace.
3. **`git_read_operations(repo_path, git_command, git_args)`**:
   - Executes **safe, read-only git commands** (such as `git log`, `git show`, `git diff`, `git status`) in the specified repo.
   - **Security Guardrail**: The Python harness rejects any unsafe git subcommands (such as `checkout`, `reset`, `commit`, `push`, `clean`) instantly to prevent repository corruption.

### 4.2. Dynamic Git History Mining:
Diagnose leverages the `git_read_operations` tool to perform high-value diagnostics:
- **Successful Run Retrieval**: Since UDMI sequencer output files are checked in for every run in the site model repository, Diagnose runs `git log` and `git show` on the failed test's `sequence.log` file to fetch its exact log contents during the **last successful execution**.
- **Code Regression Identification**: Diagnose compares git logs of the main `udmi` repository between the commit of the previous successful run and the current failure, pinpointing the exact code diffs/commits that might have introduced the regression.

---

## 5. Zero-Configuration CLI & Scoping

Diagnose utilizes a zero-configuration CLI entrypoint, completely eliminating manual target, site model, and device ID parameters.

### 5.1. CLI Wrapper (`mantis/bin/diagnose`)
Launches the module: `python3 -m mantis.diagnose.main "$@"`

### 5.2. Input Arguments
| Argument | Short Flag | Type | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `--bundles-dir` | `-i` | `string` | **Required** | Path to bundles directory containing run backups (single or multi-run). |
| `--test` | `-t` | `string` | `None` | Specific test case to triage (sweeps all failures if omitted). |
| `--target` | `None` | `string` | `None` | Target project (auto-detected from bundles-dir if omitted). |
| `--site-dir` | `None` | `string` | `None` | Path to site model folder (auto-detected if omitted). |

### 5.3. Under-The-Hood Auto-Discovery
- **Target**: Parsed directly from the bundles directory name (e.g., `staging_UK-LON-GLAB` ➔ `//staging/UK-LON-GLAB`).
- **Site Model**: Auto-matched to directories under `sites/` (e.g. `sites/staging` for target prefix `staging`, defaulting to `sites/udmi_site_model`).
- **Device ID**: Discovered dynamically by scanning the extracted `out/devices/` folder inside the run.
- **Smart Failure Scoping**:
  Before running diagnostics, the tool attempts to read the stability metrics JSON generated in Step 1. If it finds the file, it **ONLY triages test cases that actually mismatched their baseline expected outcome** (true regressions/flakiness), preventing redundant triage runs on expected negative test case failures (e.g. intentional broken config fails). If the metrics file is absent, it falls back to a raw `RESULT fail` sweep across sequencer outputs.

---

## 6. Structured Output Specifications

### 6.1. Standardized Nested Directory Tree
Diagnose saves localized reports under the self-contained `mantis/out/` hierarchy:
`mantis/out/diagnose/<project_id>/<site_id>/<device_id>/<test_id>/triage_analysis.md`

### 6.2. Structured Premium Diagnostic Report (`triage_analysis.md`)
Every localized report must conform to the following structured markdown format:

```markdown
# UDMI Sequencer Staging Run Triage Analysis: <device_id> <test_id> Failure

This document presents a comprehensive root cause analysis of the `<test_id>` test failure for device **<device_id>**...

---

## 1. Executive Defect Summary
> [Provide a concise, single-line summary starting with a blockquote '>' identifying the primary component failure and exact error, e.g. 'Sequencer assertion timed out during config sync: last_start not synced in config'. This blockquote is automatically parsed by the triage engine.]

[Provide a structured, detailed summary (3-4 bullets or paragraphs) detailing exactly how the Sequencer, UDMIS, and Device/Gateway interacted, using transaction IDs (RC:xxxxxx) and timestamps to pinpoint the defect.]

---

## 2. Component Assessment
- **Did the device work as expected?** [Yes/No/Partial + concise 1-2 sentence justification based on log evidence]
- **Did sequencer work as expected?** [Yes/No/Partial + concise 1-2 sentence justification based on log evidence]
- **Did udmis work as expected?** [Yes/No/Partial + concise 1-2 sentence justification based on log evidence]

---

## 3. Detailed Timeline of Events
Construct a clean chronological markdown table aligning all correlated logs (Sequencer, UDMIS, Gateway, Pubber/Device) to show precisely when and why the runs diverged. Format:

| Timestamp (UTC) | Source | Log Message / Event | Significance |
| :--- | :--- | :--- | :--- |
| [HH:MM:SS] | [Component] | `Log Message Snippet` | [Relevance/Significance explanation] |

---

## 4. Proposed Code Fix (or Technical Concurrency RCA)
Identify the root cause bug or race condition and propose the concrete source code modifications (including exact file paths, approximate line ranges, and a standard unified diff or code block) needed to fix the bug in the Java emulators, sequence files, or processors.

If the available logs, git history, and code logic are insufficient to isolate the breakpoint, you MUST output the header '⚠️ INSUFFICIENT DATA TO TRACE ROOT CAUSE' under this section and list exactly what log streams/configurations are missing.
```

---

## 7. Site Triage Summary Report (`triage_summary_report.md`)

The consolidated summary report compiled at `mantis/out/diagnose/<project_id>/<site_id>/triage_summary_report.md` delivers site-level engineering value:

- **Triage Dashboard**: Overall metrics (Total Checked, Total Failed, Success Rates, Failure Clusters).
- **Relative Links:** Provides hyperlinked references using relative paths (e.g., `./<device_id>/<test_id>/triage_analysis.md`) for secure local viewing.
- **Failure Clustering**: Groups identical failure profiles together:
  ```markdown
  ### ⚠️ Cluster 1: Config Ack Timeout (Affecting 4 Tests)
  - **Root Cause**: Pubber Emulator delayed config acknowledgments exceeding sequencer wait limits.
  - **Affected Tests**:
    - `valid_serial_no` ([View Details](./AHU-1/valid_serial_no/triage_analysis.md))
    - `pointset_publish` ([View Details](./AHU-1/pointset_publish/triage_analysis.md))
  ```
- **CI/CD PR Warning Block**: A highly optimized markdown summary designed to be posted directly as a PR comment, highlighting newly introduced regressions versus known flaky tests.
