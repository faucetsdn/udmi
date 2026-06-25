# Specification: collect_test_runs

## 1. Primary Purpose
The `collect_test_runs` utility is the data acquisition engine for Mantis. Its responsibility is to gather multiple executions of the test suites to provide the necessary statistical sample size for stability evaluation and automated triage.

## 2. Intended Behavior (Implementation Blueprint)
To guarantee consistent artifact generation, the implementation MUST adhere to the following sequence:
1. **Argument Parsing**: Resolve the execution mode (`local`, `ci`, `ci_search`), target run count (N), git branch/remote, and specific test filters.
2. **Execution Routing**:
   * **If Mode == Local**:
     * Invoke the local test sequencer synchronously N times in a loop.
     * Intercept standard output and test artifacts for each iteration, routing them into isolated subdirectories (e.g., `run_1`, `run_2`) within the output workspace.
   * **If Mode == CI**:
     * Authenticate with the remote CI provider API (e.g., GitHub API).
     * Dispatch N parallel workflow runs targeting the specified branch.
     * Enter a blocking polling loop with exponential backoff to monitor run states until all N runs transition to terminal states (success, failure, cancelled).
     * Download the artifact bundles for all completed runs into the local workspace.
   * **If Mode == CI Search**:
     * Query the CI provider API for the last N completed workflow runs on the specified branch.
     * Download the associated artifact bundles into the local workspace.
3. **Extraction & Normalization**: Decompress any downloaded `.zip` or `.tgz` artifacts into distinct subdirectories.
4. **Termination**: Return exit code `0` on success, or a non-zero exit code if API authentication, dispatch, or downloads fail.

## 3. Interfaces
* **Inputs**: Command-line arguments defining execution mode, targets, and credentials (via environment variables).
* **Outputs**: A consolidated local workspace containing exactly N subdirectories of raw test artifacts.

## 4. Contract Definitions

### 4.1. CLI Arguments (`argparse` signature)
* `--mode`, `-m`: Enum `['local', 'ci', 'ci_search']`. Default: `'local'`.
* `--runs`, `-n`: Integer. Number of executions. Default: `3`.
* `--target`, `-t`: String. Project specific target path (e.g., `//mqtt/localhost`).
* `--branch`, `-b`: String. Git branch for CI operations. Default: active branch.
* `--remote`, `-r`: String. Git remote. Default: `origin`.
* `--suite`: Enum `['sequencer', 'itemized', 'both']`. Default: `both`.
* `--tests`: String. Comma-separated list of test cases.

### 4.2. External Commands
* **Local Sequencer Invocation**: 
  When `mode=local`, the tool MUST execute: `bin/test_sequencer [target]` or `bin/test_itemized [target]` based on the `--suite` flag.
* **Output Pathing**:
  Artifacts MUST be stored relative to `out/mantis/run_data/run_X/`.
