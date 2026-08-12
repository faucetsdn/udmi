# Project Mantis: Autonomous AI Diagnostic Engineer & Bug Predator for UDMI

> **Mantis** is an AI-powered diagnostic and triage platform specifically designed to answer all UDMI domain questions, investigate test failures, analyze distributed MQTT telemetry, verify JSON schemas, and eliminate bugs across the UDMI codebase.

---

## Capabilities

1. **Interactive Chat Mode**: Full multi-turn diagnostic REPL equipped with domain-specific tools, autonomous artifact resolution, and dynamic context tracking.
2. **Targeted Diagnostics**: Single-command triage for specific failing tests and devices (`bin/mantis sites/<site> [device] [test]`).
3. **Natural Language Queries**: One-shot query answering with codebase/log grounding (`bin/mantis "why did pointset_publish fail for EM-11?"`).
4. **Autonomous Test Output Discovery**: Automatically discovers logs and sequence traces across all valid UDMI output directories (`sites/*/udmi/out`, `sites/*/out`, `out/`, `out_*` shards).
5. **Specialized UDMI Domain Tools**:
   - **Schema Inspector**: Query and validate 100+ schemas in `schema/` (`pointset`, `state_system`, `config_pointset`, etc.).
   - **Site Model Inspector**: Parse `cloud_iot_config.json` and device `metadata.json`.
   - **Message Trace Inspector**: Inspect recorded MQTT messages (`events_*.json`, `state_*.json`, `config_*.json`).
   - **Sequence Code Harvester**: Extract Java assertion logic from `validator/.../sequences/`.
6. **Stability Analysis & Test Collection**: Ingest multiple test run backups, evaluate flake rates, and capture automated test execution loops.

---

## Unified Command Line Interface (`bin/mantis`)

Mantis provides a single, unified entry point located at `$UDMI_ROOT/bin/mantis`:

```bash
# 1. Launch Interactive Chat Session (Default)
bin/mantis
bin/mantis chat --site sites/udmi_site_model

# 2. Run Targeted Diagnostics for a Specific Failure
bin/mantis sites/udmi_site_model AHU-1 pointset_publish
bin/mantis diagnose --site sites/udmi_site_model --device AHU-1 --test pointset_publish

# 3. One-Shot Natural Language Query
bin/mantis "Why did AHU-1 fail pointset_publish?"
bin/mantis -q "How does UDMI pointset validation handle missing units?"

# 4. Multi-Run Stability Evaluation
bin/mantis eval -i out/mantis/run_1/ out/mantis/run_2/

# 5. Batch Failure Triage from Support Bundles
bin/mantis triage -i bundle1.zip bundle2.zip

# 6. Active Test Loop Collection
bin/mantis collect --target //mqtt/localhost:46432 --runs 3

# 7. Generate Custom Triage Playbook
bin/mantis create-playbook my_custom_playbook.yaml
```

---

## Authentication & Vertex AI Options

Mantis supports both direct Gemini API Keys (`GEMINI_API_KEY`) and Google Cloud Vertex AI:

```bash
# 1. Use Google Cloud Vertex AI via CLI flag (Auto-detects active gcloud project):
bin/mantis --vertex

# 2. Or specify GCP Project and Region via --vertex <project>/<region>:
bin/mantis --vertex my-gcp-project/us-central1

# 3. Or enable Vertex AI via environment variables:
export MANTIS_USE_VERTEXAI=true
export GCP_PROJECT=my-gcp-project

# 4. Or use standard Gemini Developer API key:
export GEMINI_API_KEY="your-api-key"
```


---

## Interactive Chat Slash Commands


Inside the Mantis chat REPL (`bin/mantis`):

| Command | Description |
| :--- | :--- |
| `/help` | Show interactive help and tool manual |
| `/critique [notes]` | Run an automated adversarial critique & verification pass on the last diagnosis |
| `/site <site>` | Switch active site model (e.g. `/site sites/udmi_site_model`) |
| `/device <id>` | Switch active device under test (e.g. `/device AHU-1`) |
| `/test <name>` | Switch active test case (e.g. `/test pointset_publish`) |
| `/load <path>` | Load a `triage_manifest.json` |
| `/context` | Display current active session state and discovered artifacts |
| `/tools` | List all available diagnostic tools |
| `/clear` | Reset chat history |
| `/export <file>`| Export diagnostic conversation transcript to Markdown |
| `/exit`, `/quit` | Exit chat session |

---

## Directory Structure

```
util/mantis/
├── v1/                      # Preserved Classic Mantis v1 (Master baseline)
│   ├── bin/                 # Classic CLI executables (triage, collect, etc.)
│   ├── src/                 # Classic source code
│   └── tests/               # Classic test suite
├── v2/                      # Modern Mantis v2 Implementation
│   ├── agent/               # Chat session, prompts, entity extractor
│   │   ├── chat.py          # Interactive REPL & multi-turn agent
│   │   ├── extractor.py     # Natural language entity extraction
│   │   ├── prompts.py       # Grounded UDMI domain prompts
│   │   └── agent.py         # Test code harvester & timeline builder
│   ├── tools/               # Specialized domain & artifact tools
│   │   ├── artifacts.py     # Multi-path test artifact discovery
│   │   ├── schemas.py       # JSON schema inspector
│   │   ├── site_models.py   # Site model & metadata inspector
│   │   ├── traces.py        # MQTT message trace inspector
│   │   └── resolver.py      # Log & shard resolver
│   ├── workflows/           # Core execution workflows
│   │   ├── diagnose.py      # Multi-stage triage runner
│   │   ├── collector.py     # Test loop collector
│   │   ├── reporter.py      # Consolidated reports & clustering
│   │   └── stability/       # Multi-run stability analyzer
│   ├── engine/              # LLM engine & infrastructure
│   │   ├── engine.py        # Async LLM loop & tool-calling
│   │   ├── pipeline.py      # Dynamic stage pipeline & skills
│   │   ├── tools.py         # ToolBelt & code inspection
│   │   ├── models.py        # Pydantic structured output models
│   │   ├── context.py       # Log indexing & compaction
│   │   ├── logging.py       # Colored logger & Tee stream
│   │   └── harness/         # Credentials, rate limiter, search
│   ├── skills/              # Mantis domain skills (log-analysis, etc.)
│   ├── spec/                # Architecture specifications & guides
│   ├── config/              # Playbooks (playbook_oem.yaml, playbook_swe.yaml)
│   └── cli.py               # Unified CLI router & dispatcher
├── tests/                   # Comprehensive pytest test suite (v2)
├── setup.py                 # Setuptools package configuration
└── README.md
```

---

## Running Tests

Execute the Mantis test suite directly or via the UDMI project test runner:

```bash
# Run all Mantis test suites (both v1 and v2)
bin/test_mantis

# Or run specific versions:
bin/test_mantis --v2
bin/test_mantis --v1

# Run project-wide utility tests
bin/run_tests util_tests
```
