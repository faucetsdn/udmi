# Specification: evaluate_stability

## 1. Primary Purpose
The `evaluate_stability` utility acts as the statistical filter and metric compiler. It consumes the raw test runs gathered by the collection layer and determines the true failure state of the system by separating hard regressions from transient environmental flakes.

## 2. Intended Behavior (Implementation Blueprint)
To guarantee accurate metric compilation, the implementation MUST adhere to the following sequence:
1. **Workspace Scanning**: Scan the provided input directory to identify all valid run subdirectories. Ignore empty or malformed folders.
2. **Artifact Discovery**: Recursively search each valid run subdirectory to locate structured test outcome reports (e.g., summary JSON files or sequence reports).
3. **Matrix Compilation**:
   * Build a global cross-run evaluation matrix mapping every unique test case executed.
   * Iterate through the outcomes of all runs, recording the terminal state (Pass, Fail, Skip) of each test case per run.
4. **Categorization Logic**:
   * Calculate the failure percentage for each test case.
   * If failure rate == 100%: Categorize as `REGRESSION`.
   * If failure rate > 0% and < 100%: Categorize as `FLAKY`.
   * If failure rate == 0%: Categorize as `STABLE`.
5. **Manifest Generation**:
   * Construct a structured JSON document (`triage_manifest.json`) containing the statistical evaluation.
   * The manifest MUST clearly separate `failures` (regressions and flakes requiring triage) from `stable` tests.
   * Write the manifest atomically to the root of the input workspace directory.
6. **Termination**: Log a summary of discovered failures to standard output and exit with code `0`.

## 3. Interfaces
* **Inputs**: A workspace directory containing multiple extracted test run folders.
* **Outputs**: A compiled `triage_manifest.json` file detailing stability metrics, pass/fail run counts, and categorized failure IDs for each test case.

## 4. Contract Definitions

### 4.1. CLI Arguments (`argparse` signature)
* `--test-runs`, `-i`: (Required) String or List of Strings. The paths to the extracted run directories or bundle archives.

### 4.2. Data Schemas
* **Input Artifact**: The utility scans for test outcome files under the run directories (e.g., `sequencer.json` or `system.json`).
* **Output Artifact Schema** (`triage_manifest.json`):
```json
{
  "test_runs": 3,
  "stable": [
    {"test_name": "system_min_loglevel", "pass_count": 3}
  ],
  "failures": [
    {
      "test_name": "valid_serial_no",
      "fail_count": 2,
      "pass_count": 1,
      "status": "FLAKY"
    },
    {
      "test_name": "device_config_update",
      "fail_count": 3,
      "pass_count": 0,
      "status": "REGRESSION"
    }
  ]
}
```
