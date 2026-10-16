# Mantis: Autonomous UDMI Agent & Diagnostic Management Control Plane

Mantis is an autonomous, production-grade AI agent and management control plane for the **Universal Device Management Interface (UDMI)**, modeled after Jetski. Mantis assists developers, field engineers, and system integrators with UDMI exploration, schema validation, test execution, failure root-cause analysis, and architectural visualization.

---

## 1. Architectural Principles

1. **Codebase as Single Source of Truth (SSoT)**:
   Mantis does not hardcode schemas, state transition tables, or device failure rules. The UDMI repository (`schema/`, `docs/specs/`, `docs/messages/`, and Java sequencer classes) is always inspected dynamically to prevent documentation and implementation drift.

2. **Two-Tier Model Routing** (`MantisAgent.classify_intent_tier` in `agent.py`):
   Each prompt is routed to one tier for the whole run, by keyword:
   - **Flash Tier** (`gemini-3.7-flash`, `MANTIS_FLASH_MODEL`): simple lookups such as listing devices, showing a schema, or slicing a log. The Actor answers alone; there is no scoping and no review.
   - **Pro Tier** (`gemini-3.1-pro-preview`, `MANTIS_PRO_MODEL`, thinking level `MANTIS_THINKING_LEVEL`, default `high`): explanations ("how does", "explain"), failures, diffs, patches, test execution, and anything that matches no keyword.

3. **Scoping, then the Tripartite Loop (`Actor -> Critic -> Arbitrator`)** on the Pro tier:
   - **Scoping**: classifies the prompt. A failure gets competing hypotheses the run must resolve; a question that reports no failure (`NOT_A_FAILURE`) is answered in informational mode, without hypotheses.
   - **Actor**: explores the codebase, executes tools, harvests log evidence, and drafts the answer.
   - **Critic**: audits the draft against the tool evidence and flags ungrounded claims. In informational mode the Critic receives source reads (`read_udmi_file`, `inspect_sequencer_test`, `inspect_udmi_schema`, `locate_udmi_doc`) in full, so it does not call a correct claim unverified because the excerpt was cut.
   - **Arbitrator**: reconciles the draft with the Critic's findings and produces the final answer, adding diagrams where they help. Triage answers end with a `## Hypothesis Resolution Audit` section.
   - **Cancellation**: `run(..., cancel_event=threading.Event())` stops the run at the next step boundary (before a ReAct step, a tool call, forced synthesis, the Critic, or the Arbitrator) by raising `MantisCancelled`. The Workbench's Stop button uses this.

4. **Dual Visualization Engine**:
   - **Graphviz (DOT)**: Generates structured, hierarchical architecture and network topology graphs compiled to SVG via the local `/usr/bin/dot` utility.
   - **Mermaid**: Generates sequence diagrams and protocol transaction flows (`sequenceDiagram`, `graph LR`).

---

## 2. Tooling Suite

Mantis exposes granular tool primitives across five functional tiers:

### Tier 1: Environment & Execution Control
- `ensure_test_setup`: Provisions an isolated local UDMI stack (Mosquitto broker, etcd, InfluxDB, PostgreSQL, UDMIS) in isolated tmux windows with non-privileged user-space ports.
- `terminate_test_setup`: Safely tears down isolated test sessions.
- `list_test_setups`: Lists active test infrastructure sessions.
- `list_test_windows`: Inspects named tmux windows (`main`, `dut`, `sequencer`, `butler`, `validator`).
- `get_test_logs`: Captures live console output from named windows.
- `run_sequencer_test`: Launches sequence tests against local or cloud endpoints (`//gbos/...`, `//gcp/...`).
- `start_session_process`: Launches a command inside a named window of an active session (sequencer, custom Pubber, monitoring scripts).

### Tier 2: Real-time Inspection & Mutation
- `query_database`: Executes read-only SQL or Flux queries against isolated PostgreSQL or InfluxDB instances.
- `publish_mqtt_message`: Injects raw MQTT payloads for active triage.

### Tier 3: Specification & Site Model Grounding
- `inspect_udmi_schema`: Inspects official JSON schemas under `schema/` with recursive `$ref` resolution.
- `inspect_site_model`: Validates site models, device counts, and device metadata definitions.
- `patch_site_model`: Safely updates device `metadata.json` with automatic `.bak` backups and unified diff generation.

### Tier 4: Diagnostic Intelligence & Codebase Traversal
- `read_udmi_file`: Reads exact lines from source code, schemas, or specification documents.
- `search_codebase`: Fast ripgrep search across Java, Python, JSON, and Markdown files.
- `locate_udmi_doc`: Finds authoritative specs and guides in `docs/` matching a topic.
- `inspect_sequencer_test`: Dynamically parses Java test classes in `validator/.../sequencer/sequences/`, extracting features, stages, and assertions.
- `inspect_message_trace`: Inspects recorded MQTT message payloads (`events_pointset.json`, `state.json`, `config.json`).
- `detect_log_anomalies`: Identifies timing anomalies, clock drift, lagging state updates, and Jackson deserialization errors.
- `get_test_timeline`: Extracts chronological ISO timestamps, transaction IDs (`RC:...`), and cutoff transitions from logs.
- `compare_test_runs`: Performs behavioral differential sequence alignment between a target run and a reference baseline.
- `diagnose_test_failure`: Harvests a deterministic timeline, transaction IDs, and cutoff thresholds from a recorded run and checks a fixed catalogue of known failure modes. Workbench triage calls it first and treats its output as evidence to verify, not as the conclusion.
- `evaluate_test_stability`: Computes reliability score, flakiness index, and failure-mode distribution across several runs.
- `get_udmis_runtime_logs`: Retrieves authoritative UDMIS runtime execution logs across local files and cloud sinks, strictly enforcing evidence-tier boundaries (`LOCAL_FILE`, `CLOUD`, `NONE`, `UNAVAILABLE`, `INDETERMINATE`).
- `verify_golden_baseline`: Validates test outputs against golden baselines in `etc/` enforcing anti-cheating rules.

### Tier 5: Dual Visualization Engine
- `generate_topology_diagram`: Produces Graphviz DOT and Mermaid topology diagrams from site models.
- `generate_sequence_diagram`: Produces Graphviz DOT and Mermaid sequence diagrams from test execution logs.
- `render_dot_to_svg`: Compiles Graphviz DOT syntax to SVG using local `/usr/bin/dot`.

---

## 3. Navigational Skills (Playbooks)

Mantis uses procedural playbooks in `mantis/skills/` that teach the agent where and how to investigate the codebase:
1. `udmi-spec-navigation`: Locates authoritative specifications and message definitions.
2. `state-machine-investigation`: Analyzes config-state synchronization and transaction timestamps.
3. `sequencer-test-anatomy`: Parses Java sequencer test classes, `@Feature` annotations, and assertions.
4. `gateway-fieldbus-triage`: Investigates proxy devices, gateway bindings, and fieldbus error telemetry.
5. `log-anomaly-hunting`: Cross-references multi-service logs chronologically to isolate failure causes.
6. `database-inspection`: Validates telemetry persistence in PostgreSQL and InfluxDB.
7. `transport-and-target-routing`: Navigates target project specifications, broker URIs, and transport routing.
8. `udmi-architecture-and-components`: Maps component boundaries, evidence tiers, and UDMIS processor pipelines.

---

## 4. Usage

### Model provider credentials
Mantis needs a Gemini model provider. It is selected from the environment, in this order:

| Provider | How to configure |
|----------|------------------|
| Offline deterministic (no LLM) | `export MANTIS_OFFLINE=1` or `bin/mantis --offline` |
| Google AI Studio | `export GEMINI_API_KEY=<key>` (or `GOOGLE_API_KEY`); create a key at https://aistudio.google.com/apikey |
| Vertex AI | `gcloud auth application-default login`, then the one-time setup `bin/mantis setup --vertex=<your-project-id>[/<region>]` (region default `global`). For one shell instead: `export GOOGLE_CLOUD_PROJECT=<your-project-id>` (region: `GOOGLE_CLOUD_REGION`), or `bin/mantis --vertex=<project>/<region>` for one run. |

For Vertex AI, a project named by the application-default credentials also works when
`GOOGLE_CLOUD_PROJECT` is unset.

`bin/mantis` and `bin/workbench` check their prerequisites before starting
(`mantis/preflight.py`, `workbench/server/preflight.py`): Python modules, the UDMI common
package, `tmux`, and a model provider (not needed for `bin/mantis --mcp`). They exit and
list every problem with its fix. Both first run `bin/ensure_venv`, which runs
`bin/setup_base` when `venv/` is missing or out of date with `etc/requirements.txt`.

The one-time setup is saved under the `mantis` key of `~/.config/udmi/workbench.json`
(`mantis/provider_setup.py`); `bin/mantis setup` shows it and `bin/mantis setup --clear` removes it.
A saved setup is applied at startup by `bin/mantis`, `bin/workbench` and the Workbench gateway. If the environment names a different provider (`GEMINI_API_KEY`, `GOOGLE_API_KEY`, `MANTIS_OFFLINE`, or a different `GOOGLE_CLOUD_PROJECT` / `GOOGLE_CLOUD_REGION`), they refuse to start and name both; unset the variable or run `bin/mantis setup --clear`.

### Interactive Console
```bash
bin/mantis
```
Launches an interactive REPL session with conversational memory, streaming token generation, and command controls (`/status`, `/logs [window]`, `/clear`, `/export [file]`, `/help`, `/exit` or `/quit`).

### Headless Execution
```bash
# Diagnostic query
bin/mantis "Why did pointset_publish fail for AHU-1?"

# Codebase exploration
bin/mantis "Find spec for writeback"

# Visual topology diagram
bin/mantis "Show topology diagram for sites/udmi_site_model"

# Schema inspection
bin/mantis "What are the required fields in pointset schema?"

# Test execution
bin/mantis "Run pointset_publish for AHU-1 in session dev_1"
```

### Support Bundle Ingestion
```bash
bin/mantis support_bundle.zip
```
Automatically extracts, ingests, and performs root-cause analysis on an archived diagnostic bundle (`.zip`, `.tar.gz`, or `.tgz`).

### Workbench
The Workbench's Mantis drawer runs the same `MantisAgent` through `workbench/server/mantis_adapter.py`, which streams the run as Server-Sent Events (Contract 2 in [`workbench/WORKBENCH_CONTRACTS.md`](../workbench/WORKBENCH_CONTRACTS.md)). See [`docs/tools/workbench.md`](../docs/tools/workbench.md) for the user guide.

### Model Context Protocol (MCP) Server
```bash
bin/mantis --mcp
```
Runs a standard stdio MCP server exposing all registered Mantis tools to external agent environments.

### Python API
```python
from mantis.agent import MantisAgent

agent = MantisAgent()

# Single-shot execution
response = agent.run("Why did pointset_publish fail for AHU-1?")

# Tripartite execution (Actor -> Critic -> Arbitrator), returning every stage's output
result = agent.run_tripartite("Explain device state transitions")
print(result["final_answer"])

# Cancellable run with structured progress records (what the Workbench does)
import threading
cancel = threading.Event()
answer = agent.run("How does scan_single_future work?",
                   event_callback=print, cancel_event=cancel)  # cancel.set() from another thread stops it
```

---

## 5. Testing

Run the comprehensive Mantis test suite:
```bash
bin/test_mantis
```
