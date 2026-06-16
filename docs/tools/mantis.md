[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Mantis](#)

# Mantis

Mantis is an automated diagnostic tool that triages UDMI test failures. It evaluates stability across multiple test runs and generates Root Cause Analysis (RCA) reports by correlating logs across the Sequencer, UDMIS, and Pubber.

# Setup

Mantis requires access to Google's Gemini models for diagnostics.

**Option A: Developer API Key (Public Endpoint)**
```bash
export GEMINI_API_KEY="your_gemini_api_key"
```

**Option B: Enterprise GCP Vertex AI (ADC Endpoint)**
Source the `enable_vertex` script to auto-detect your active `gcloud` project or set an explicit project ID:
```bash
source tools/mantis/bin/enable_vertex [gcp_project_id]
```

# Running Mantis

Run `bin/triage` from the UDMI root directory. Provide test bundle archives (`.zip`, `.tgz`) or extracted directories as input via `-i`.

```bash
tools/mantis/bin/triage -i path/to/bundle1.zip path/to/bundle2.zip
```

Alternatively, point to extracted directories:
```bash
tools/mantis/bin/triage -i out/mantis/run_1/ out/mantis/run_2/
```

## CLI Reference (`bin/triage`)

| Argument | Description | Example |
|---|---|---|
| `-i`, `--test-runs` | **(Required)** Test bundle paths or extracted directories (space-separated). | `-i path/run1.zip path/run2.zip` |
| `-p`, `--project-path`| Path to the UDMI project root. Use when testing against a different branch or PR directory. | `-p ~/Projects/udmi/pr-branch` |
| `-d`, `--device` | Filter triage by specific device ID. | `-d AHU-1` |
| `-t`, `--test` | Filter triage by specific test case name. | `-t system_min_loglevel` |
| `--all` | Force triage of all identified failed tests. | `--all` |
| `--swe` | Use the SWE software debugging playbook instead of the default OEM integrator playbook. | `--swe` |
| `-h`, `--help` | Show usage instructions. | `-h` |

# Utilities

## Active Test Loop Collection

Use `collect_test_runs` to actively execute new test loops locally or fetch historical CI runs:
```bash
# Run the sequencer locally 3 times in a row
tools/mantis/bin/collect_test_runs --mode local --target //mqtt/localhost --runs 3

# Fetch the last 5 completed CI runs from GitHub
tools/mantis/bin/collect_test_runs --mode ci_search --runs 5
```

### CLI Reference (`bin/collect_test_runs`)

| Argument | Description | Example |
|---|---|---|
| `-t`, `--target` | Target project specification. Use 'skip' to omit from CI. | `--target //mqtt/localhost` |
| `-n`, `--runs` | Number of loops or historical runs to retrieve (default: 3). | `--runs 5` |
| `-m`, `--mode` | Execution mode: `local` (sandbox), `ci` (dispatch new), `ci_search` (retrieve past). | `--mode ci_search` |
| `-b`, `--branch` | GitHub branch to target or search (default: active branch). | `--branch master` |
| `-r`, `--remote` | Git remote name to use for GitHub API interactions (default: origin). | `--remote upstream` |
| `--suite` | Test suite to run locally (`sequencer`, `itemized`, `both`). | `--suite sequencer` |
| `--tests` | Comma-separated list of selective tests to run locally. | `--tests valid_serial_no` |
| `--verbose` | Monitor logs foreground. | `--verbose` |
| `-h`, `--help` | Show usage instructions. | `-h` |

## Custom Playbook Creator

If you need specialized AI behavior (e.g., custom prompts or different concurrency limits), use the interactive playbook generator. Note that this is a fully interactive terminal wizard and does not accept standard command-line arguments:
```bash
tools/mantis/bin/create_playbook
```
This utility will ask for your preferences and generate a new custom YAML playbook in your current directory. You can then pass it to Mantis using the `--playbook` flag:
```bash
tools/mantis/bin/triage -i out/mantis/run_data --playbook ./my_custom_playbook.yaml
```

# Output Artifacts & Interpreting Results

Mantis generates reports in the output directory (e.g., `out/mantis/<run_name>/`):

### 1. `triage_manifest.json`
An internal JSON map separating transient flakes from regressions, used by the diagnostics engine.

### 2. `triage_summary_report.md`
A high-level summary of pass/fail metrics across all runs. It groups failures by test case and provides a 1-sentence AI Root Cause Analysis. Start here to prioritize fixes.

### 3. `diagnose/.../triage_analysis.md`
Detailed diagnostic reports for each failure ID. Each report contains:
1. **Mechanism of Failure**: What broke.
2. **Flakiness Vector**: Conditions causing the failure.
3. **Timeline Evidence**: Chronological log of events across Sequencer, Device, and UDMIS.
4. **Root Cause**: The underlying code or configuration flaw.
5. **Recommendation**: Proposed fixes or configuration updates.

## Specialized Playbooks

By default, Mantis uses the **OEM & Systems Integrator Compliance Playbook**. When testing physical hardware or black-box devices, Mantis will:
* Act as a strict protocol auditor.
* Analyze TLS negotiations, auth drops, and JSON schema payloads.
* Propose "Requests for Information" to hardware vendors.

When developing the local emulator (Pubber) or internal framework software, use the **SWE (Software Engineer) Playbook** instead. It changes the behavior to:
* Act as a software debugger and audit internal thread loops.
* Trace variables and propose Java source-code fixes.

*(To switch to this behavior, pass the `--swe` flag to the triage script.)*
