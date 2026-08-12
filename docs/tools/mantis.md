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
Auto-detect your active `gcloud` project or pass explicit project credentials:
```bash
bin/mantis --vertex
# Or set environment variables:
export MANTIS_USE_VERTEXAI=true
export GCP_PROJECT=my-gcp-project
```

# Running Mantis

Run **`bin/mantis`** from the UDMI root directory.

```bash
# 1. Launch Interactive Chat Mode (Default)
bin/mantis

# 2. Targeted Diagnostics for a Failing Test
bin/mantis sites/udmi_site_model AHU-1 pointset_publish

# 3. Batch Triage from Test Bundles
bin/mantis triage -i path/to/bundle1.zip path/to/bundle2.zip

# 4. Multi-Run Stability Evaluation
bin/mantis eval -i out/mantis/run_1/ out/mantis/run_2/

# 5. Run Classic Mantis v1
bin/mantis --v1 triage -i path/to/bundle1.zip
```

## Interactive Chat Mode (`bin/mantis` or `bin/mantis chat`)

Mantis includes an interactive **Chat Mode** allowing developers and integrators to have multi-turn conversations with the diagnostic agent, ask follow-up questions, drill into specific log intervals, and inspect schemas dynamically:

```bash
# Launch interactive chat mode with a test bundle
bin/mantis chat -i out/mantis/run_1/

# Execute a one-shot query from the terminal
bin/mantis "Why did AHU-1 fail pointset_publish?"
```

### Chat Slash Commands

Inside the chat REPL, the following slash commands are available:
* `/help`: Display available chat commands.
* `/load <path>`: Load a new test bundle directory or `triage_manifest.json`.
* `/device <device_id>`: Set or switch active target device (e.g. `AHU-1`).
* `/test <test_name>`: Set or switch active test case (e.g. `system_min_loglevel`).
* `/tools`: List all registered diagnostic tools.
* `/context`: View active session context and targets.
* `/clear`: Clear conversation history.
* `/export [file]`: Export the diagnostic chat session transcript to markdown.
* `/exit`: Exit interactive chat session.

## CLI Reference (`bin/mantis`)

| Command / Option | Description | Example |
|---|---|---|
| `bin/mantis` | Launch Mantis in interactive Chat Mode REPL. | `bin/mantis` |
| `bin/mantis <site> [dev] [test]` | Perform targeted diagnostics on a specific test failure. | `bin/mantis sites/udmi_site_model AHU-1 pointset_publish` |
| `bin/mantis triage -i <runs>` | Batch triage of test bundles or extracted directories. | `bin/mantis triage -i run1.zip run2.zip` |
| `bin/mantis eval -i <runs>` | Multi-run flake rate and stability evaluation. | `bin/mantis eval -i run_1/ run_2/` |
| `bin/mantis collect [opts]` | Actively execute test loops or retrieve CI runs. | `bin/mantis collect --runs 3` |
| `-q`, `--query` | Execute a single natural language query against Mantis and exit. | `bin/mantis -q "Explain failure"` |
| `--v1` | Dispatch to classic Mantis v1 implementation. | `bin/mantis --v1 diagnose` |
| `--v2` | Dispatch to modern Mantis v2 implementation (Default). | `bin/mantis --v2` |
| `-h`, `--help` | Show usage instructions. | `bin/mantis -h` |


# Utilities

## Active Test Loop Collection

Use `bin/mantis collect` to actively execute new test loops locally or fetch historical CI runs:
```bash
# Run the sequencer locally 3 times in a row
bin/mantis collect --mode local --target //mqtt/localhost --runs 3

# Fetch the last 5 completed CI runs from GitHub
bin/mantis collect --mode ci_search --runs 5
```

### CLI Reference (`bin/mantis collect`)

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

If you need specialized AI behavior (e.g., custom prompts or different concurrency limits), use the interactive playbook generator:
```bash
bin/mantis create-playbook my_custom_playbook.yaml
```
This utility will ask for your preferences and generate a new custom YAML playbook in your current directory. You can then pass it to Mantis using the `--playbook` flag:
```bash
bin/mantis triage -i out/mantis/run_data --playbook ./my_custom_playbook.yaml
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
