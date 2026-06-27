# Specification: diagnose

## 1. Primary Purpose
The `diagnose` utility is the core AI reasoning and Root Cause Analysis (RCA) engine for Mantis. It consumes the failure manifest and raw logs to orchestrate a playbook-driven GenAI session, autonomously identifying the underlying cause of software defects and hardware non-compliance.

## 2. Intended Behavior (Implementation Blueprint)
To guarantee deterministic reasoning and safeguard system limits, the implementation MUST adhere to the following execution sequence:
1. **Initialization & Parsing**:
   * Parse the input workspace directory and target Playbook YAML configuration.
   * Read the `triage_manifest.json` to extract the list of target failure test IDs and device IDs. Filter the list if CLI overrides were provided.
   * Initialize the localized Vectorized Semantic Cache and GenAI client configurations.
2. **Per-Failure Execution Loop**: For every targeted failure identified in the manifest:
   * **Data Slicing**: Locate the failure sequence within the raw output. Extract a precise, time-bounded log snippet representing the failure context.
   * **Cache Interception**: Normalise the failure snippet (strip timestamps, IDs, addresses) and query the Semantic Cache. 
     * If the cosine similarity matches a cached historical root cause above the threshold, immediately yield the cached RCA report and bypass AI execution.
     * If a cache miss occurs, proceed to pipeline orchestration.
   * **Pipeline Orchestration**: Load the Playbook stages (e.g., Timeline Harvesting, Analyst, Critique).
     * For each active stage, append the system instruction and execute the GenAI loop.
     * **Tool Invocation**: If the model requests a tool call, execute the corresponding function (e.g., `grep_codebase`, `read_file_lines`). Condense the tool output structurally to prevent context bloating, and append the result to the conversation history.
     * Iterate the tool loop until the model outputs the final analysis adhering to the required markdown headers.
   * **Result Persistence**: Write the output of the completed stages to markdown artifacts (e.g., `triage_analysis.md`) and a structured `triage_analysis.json` file inside the failure's local workspace subdirectory. Update the Semantic Cache with the new resolution.
3. **Termination**: Once all failures are processed, compile an overarching `triage_summary_report.md` at the workspace root containing high-level verdicts. Exit with code `0`.

## 3. Interfaces
* **Inputs**: The `triage_manifest.json` file, raw test sequence logs, and the Playbook YAML configuration.
* **Outputs**: 
  - An overarching `triage_summary_report.md` summarizing all failures and their high-level causes.
  - Granular, structured markdown (`triage_analysis.md`) and JSON (`triage_analysis.json`) diagnostic reports for each specific failure ID.

## 4. Contract Definitions

### 4.1. CLI Arguments (`argparse` signature)
* `--test-runs`, `-i`: String. Input directory containing the runs and the `triage_manifest.json`.
* `--manifest`, `-m`: String. Explicit path to manifest.
* `--id`: List of Strings. Specific failure IDs to triage (overrides manifest).
* `--test`, `-t`: List of Strings. Specific test names to triage.
* `--device`, `-d`: List of Strings. Specific device IDs.
* `--playbook`: String. Path to the custom playbook YAML.
* `--force`, `-f`: Boolean flag. If set, bypasses the semantic cache entirely.

### 4.2. Data Schemas
* **Playbook YAML Schema**:
```yaml
pipeline:
  default_model: string
  flash: string
  max_loops: int
stages:
  <stage_name>:
    enabled: boolean
    model: string (optional)
    system_instruction: string
    headers: list of strings
    tools: list of strings
```
* **Output Diagnostic Schema** (`triage_analysis.json`):
```json
{
  "target_id": "test_failure_123",
  "status": "SUCCESS",
  "verdict": "VERIFIED_DEFECT",
  "summary": "Short explanation",
  "root_cause_analysis": {
    "culprit_file": "path/to/file",
    "culprit_line_range": "L10-L20",
    "explanation": "Detailed explanation"
  }
}
```
