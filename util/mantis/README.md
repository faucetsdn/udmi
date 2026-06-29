# Project Mantis: The Ultimate Bug Predator

> **Mantis** is a ruthless, highly efficient predator of bugs—specifically designed to hunt down, capture, triage, and eliminate issues in the UDMI codebase.

Mantis is structured into independent, highly modular execution submodules following the Unix philosophy. Their execution stages naturally form a complete test capture, stabilization evaluation, and AI triage loop:

1. **`collect_test_runs` (Stage 1)**: Automated capture of test execution loops. Operates locally via sandboxed test runs, dispatches new parallel workflows on GitHub Actions, or downloads historical completed CI runs, packaging all metrics and console streams into sharded support bundles.
2. **`evaluate_stability` (Stage 2)**: A pure metrics and log analyzer that ingests sharded run folders, extracts results, correlates them against normalized golden baselines, identifies regressions versus expected test failures, and outputs stability reports alongside a target `triage_manifest.json`.
3. **`diagnose` (Stage 3)**: An AI-powered diagnostic triage agent that ingests the manifest, correlates sharded execution logs using padded execution timebounds, crawls codebase repositories/git histories, executes progressive multi-stage playbook tasks, and generates rich defect triage reports.

---

## Project Directory Structure

The actual layout of Project Mantis is structured as follows:

```
mantis/
├── README.md                 # Project Mantis Overview & Ecosystem
├── docs/
│   ├── mantis_triage_hld.md      # High-Level Architectural Design (HLD)
│   ├── generic/                  # Reusable Core Engine Specs (Generic SDK)
│   │   ├── authentication.md     # Authentication & ADC Setup
│   │   ├── cache_management.md   # Namespaced Semantic Cache Spec
│   │   ├── generic_triage_harness_guide.md # Harness Onboarding Guide
│   │   ├── output_integration.md # JSON Report Schema Specs
│   │   ├── playbook_specification.md # Playbook Declarative YAML Specs
│   │   ├── plugin_developer_guide.md # Language-Agnostic IPC Protocol
│   │   ├── plugin_safety.md      # Plugin Crash isolation & safety
│   │   └── rate_limiting.md      # Outbound Rate limits & Fail-open timeouts
│   └── udmi/                     # UDMI-Specific Application Specs
│       ├── evaluate_stability_spec.md # Stage 2 Stability Specs
│       └── telemetry_correlation_guide.md # Trace IDs log correlation rules
├── bin/                      # Executable Bash wrappers
│   ├── collect_test_runs         # Launches Stage 1 (Test Run Collector)
│   ├── evaluate_stability    # Launches Stage 2 (Stability Evaluator)
│   └── diagnose              # Launches Stage 3 (AI Diagnostics Triage)
│   └── support_process       # Helper utility for sharded runs extraction
│   └── web_ui                # Web interface for visual dashboard
└── src/                      # Source code
    ├── util/
    │   ├── collect_test_runs/    # Submodule 1: Trigger & Run loop
    │   ├── eval_sequencer_stability/ # Submodule 2: Stability Evaluator
    │   └── logging.py        # Shared logging utilities (e.g. Tee)
    ├── engine/               # Submodule 3: Reusable Core Triage Engine (Generic SDK)
    │   ├── harness/          # Core pipeline & agent engine
    │   └── config/           # Cache and Playbook parsers
    └── app/                  # Submodule 3 App: UDMI Triage Application
        ├── agent.py          # UDMI pipeline overrides
        ├── resolver.py       # UDMI log sharding resolver
        └── runner.py         # UDMI test-suite orchestrator

```

All execution outputs, raw bundles, reports, and persistent logs are kept modularly organized under the active execution directory (e.g. inside the test bundle folder under `out/mantis/`).

---

## 1. Test Run Collector (`collect_test_runs`)

Runs test execution loops locally, dispatches parallel CI runs on GitHub Actions, or pulls completed historical runs from GitHub.

```bash
# Local sandbox loop runs (default: 3 iterations)
mantis/bin/collect_test_runs --mode local --target //mqtt/localhost --runs 5

# Trigger new parallel GitHub Actions CI dispatches (runs in background)
export GITHUB_TOKEN="your_github_personal_access_token"
mantis/bin/collect_test_runs --mode ci --target //mqtt/localhost --runs 3

# Retrieve last 5 completed historical runs from GitHub CI
mantis/bin/collect_test_runs --mode ci_search --branch main --runs 5 --target //mqtt/localhost
```

### Command Options:
* `--mode`, `-m`: Execution mode: `local` (local sandbox), `ci` (dispatch fresh CI runs), or `ci_search` (download past completed CI runs). (Default: `local`)
* `--target`, `-t`: Target project specification under test (default: `//mqtt/localhost`).
* `--runs`, `-n`: Number of local loops, parallel CI workflow dispatches, or historical completed runs to download (default: `3`).
* `--branch`, `-b`: Git branch to target or search on GitHub (default: active branch).
* `--verbose`: Runs local sandbox execution in the foreground, showing execution logs live.
* `--suite`: Local test suite choice: `sequencer`, `itemized`, or `both` (default: `both`).
* `--tests`: Comma-separated list of selective sequencer tests to run locally (e.g. `valid_serial_no`).

---

## 2. Stability Evaluator (`evaluate_stability`)

Aggregates sharded runs from a bundles folder, matches outcomes against normalized golden baselines, computes overall flakiness indicators, and outputs stabilizing deltas.

```bash
mantis/bin/evaluate_stability \
  --bundles-dir out/mantis/ci_search_20260601_140735/ \
  --target //mqtt/localhost
```

### Command Options:
* `--bundles-dir`, `-i`: Directory containing captured sharded runs (e.g. `run_1`, `run_2` directories, or `.zip`/`.tgz` backups). **Required**.
* `--compare`, `-c`: Additional bundles directories to compare chronologically against the primary `--bundles-dir` to evaluate stabilization evolution.
* `--target`, `-t`: Target project spec (default: auto-detected from `bundles-dir` if omitted).

### Outputs Generated:
All outputs are written directly under the active bundles folder for self-containment:
* **Flakiness Report**: `flakiness_report_{clean_target}_{timestamp}.md` (detailing pass rates, expected vs actual failure matrices, and flaky test breakdowns).
* **Metrics Cache**: `metrics_{clean_target}_{timestamp}.json` (raw results cache).
* **Triage Manifest**: `triage_manifest.json` (mapping sharded log paths for all failed/flaky test shards to prepare for Stage 3 diagnostics).
* **Stabilization Evolution Report**: `stability_comparison_{clean_target}.md` (produced only when `-c` is specified, providing stabilization matrix progress like `Stabilized!`, `Improved`, `Regressed`).

---

## 3. Diagnostics & Triage Agent (`diagnose`)

An AI-powered diagnostic triage agent that performs padded timebound-primary correlation across distributed logs (Sequencer, UDMIS, Pubber), crawls codebase git history, executes playbooks sequentially, and identifies exact root cause breakpoints.

```bash
# --------------------------------------------------------------------
# OPTION A: Developer API Key (Public Endpoint)
export GEMINI_API_KEY="your_gemini_api_key"

# OPTION B: Enterprise GCP Vertex AI (ADC Endpoint - virtually unlimited quota within Google)
export MANTIS_USE_VERTEXAI=true
export GCP_PROJECT="your-gcp-project-id" # Optional, auto-detected if omitted

# --------------------------------------------------------------------
# Run Manifest-driven active triage
mantis/bin/diagnose -m out/mantis/ci_search_20260601_140735/triage_manifest.json

# Targeted single-test diagnostic sweep
mantis/bin/diagnose -m out/mantis/ci_search_20260601_140735/triage_manifest.json -t system_min_loglevel
```

### Command Options:
* `--manifest`, `-m`: Path to the compiled JSON manifest file (e.g. `triage_manifest.json`) to activate Manifest Mode.
* `--bundles-dir`, `-i`: Folder containing captured sharded runs (scans runs for all failures if manifest is omitted).
* `--test`, `-t`: List of specific test cases to triage (sweeps all discovered failures if omitted).
* `--device`, `-d`: List of specific device IDs to filter triage scope.
* `--concurrency`, `-c`: Maximum parallel triage executions (default: `3`).
* `--sequence-log`, `-sl`: Direct path to sequencer test log file (for manual standalone log triage).
* `--device-log`, `-dl` / `--udmis-log`, `-ul` / `--success-log`, `-scl`: Direct paths overrides for pubber, udmis, or successful sequencer baseline log.
* `--target` / `--site-dir`: Overrides for target project identifier or site model configuration path.

### Outputs Generated:
All outputs are structured dynamically under the active bundle's directory:
* **Persistent Console Capture**: `diagnose.log` (captures complete triage terminal output and execution stages).
* **Localized Failure Report**: `<active_bundle_dir>/diagnose/<project_id>/<site_id>/<device_id>/<test_id>/`
  * `triage_analysis.md`: Premium executive triage report, isolated root cause breakpoint, and proposed unified diff code fixes.
  * `stage_timeline.md`, `stage_intent.md`, `stage_analysis.md`, `stage_critique.md`: Sequential intermediate results from playbook pipeline stages.
* **Consolidated Site Triage Report**: `<active_bundle_dir>/diagnose/<project_id>/<site_id>/triage_summary_report.md` (site-level metrics dashboard, failure clustering signatures, and PR warning comment blocks).

---

## 4. Testing & Verification

Mantis uses the standard Python `unittest` framework. To run all unit tests hermetically inside the virtual environment:

1. **Activate the Virtualenv**:
   ```bash
   source ../../venv/bin/activate
   ```

2. **Execute the Test Suite**:
   ```bash
   PYTHONPATH=src python3 -m unittest discover tests
   ```

### Mocking Custom Pluggable Tools
When writing unit tests for custom playbook plugins or extensions:
* Mock out downstream LLM calls using `unittest.mock.patch` and `AsyncMock`.
* Verify that plugin crashes are isolated by the plugin safety layer (expecting `"status": "UNVERIFIED_PLUGIN_FAILURE"`).
* Test rate-limiting wait times by exhausting tokens and asserting that the pipeline concludes early using the fail-open fallback.
