[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Mantis](#)

# Mantis

Mantis is an AI agent for UDMI. It answers questions about the UDMI specification, schemas,
and sequencer tests, and it diagnoses sequencer test failures from the recorded run
artifacts. It works from the repository itself: it reads the schemas under `schema/`, the
documents under `docs/`, the sequencer Java source, and the logs a run left on disk, and
cites what it read. It can also bring up a local test stack, run sequencer tests, and
inspect the local database and MQTT traffic.

Mantis is available in the [Workbench](workbench.md) (the Mantis drawer) and on the command
line. Developer documentation, including the full tool list, is in
[`mantis/README.md`](../../mantis/README.md).

## Setup

Mantis needs a Gemini model provider. It picks one from the environment, in this order:

| Provider | Setup |
|---|---|
| Offline (no model; deterministic tools only) | `export MANTIS_OFFLINE=true` or pass `--offline` |
| Google AI Studio | `export GEMINI_API_KEY=<key>` (or `GOOGLE_API_KEY`); create a key at `https://aistudio.google.com/apikey` |
| Vertex AI (default) | One-time setup: `gcloud auth application-default login`, then `bin/mantis setup --vertex=<project>[/<region>]`. Or, for one shell, `export GOOGLE_CLOUD_PROJECT=<project>` (or let the credentials name the project). |

For Vertex AI the region defaults to `global`; set `GOOGLE_CLOUD_REGION` to change it, or
pass both on the command line with `bin/mantis --vertex=<project>/<region>`. There is no
built-in project.

`bin/mantis setup` shows the saved setup and `bin/mantis setup --clear` removes it. It is saved
under the `mantis` key of `~/.config/udmi/workbench.json`. A saved setup is applied at startup by `bin/mantis`, `bin/workbench` and the Workbench gateway. If the environment names a different provider (`GEMINI_API_KEY`, `GOOGLE_API_KEY`, `MANTIS_OFFLINE`, or a different `GOOGLE_CLOUD_PROJECT` / `GOOGLE_CLOUD_REGION`), they refuse to start and name both; unset the variable or run `bin/mantis setup --clear`.

## Running Mantis

Run `bin/mantis` from the UDMI root. Before it starts:

1. `bin/ensure_venv` runs `bin/setup_base` when `venv/` is missing or was built from a
   different `etc/requirements.txt`.
2. Mantis checks its Python modules, the UDMI common package, `tmux`, and (except with
   `--mcp`) a model provider. If anything is missing, it lists every problem with its fix
   and exits. Install `tmux` with `sudo bin/setup_base`.

```bash
bin/mantis                                               # interactive session
bin/mantis "Why did pointset_publish fail for AHU-1?"    # one question, answer printed, then exit
bin/mantis "How does the test scan_single_future work?"
bin/mantis support_bundle.zip                            # triage a support bundle (.zip, .tar.gz, .tgz)
bin/mantis --mcp                                         # stdio MCP server exposing the Mantis tools
bin/mantis --help
```

### Interactive session commands

| Command | Action |
|---|---|
| `/help` | List the commands. |
| `/status` | Provider, models, active site model / device / test, loaded skills, running local stacks. |
| `/logs [window]` | Recent output of a tmux window (`main` by default) of the active local stack. |
| `/clear` | Clear the conversation history (running stacks are kept). |
| `/export [file]` | Save the conversation as markdown (default `mantis_report_<timestamp>.md`). |
| `/exit`, `/quit` | Leave the session. |

The Workbench's Mantis drawer supports `/help`, `/status`, `/logs <window>`, and `/clear`.

## How Mantis answers

1. **Routing.** Each question runs on one of two model tiers: a fast tier
   (`gemini-3.7-flash`, override with `MANTIS_FLASH_MODEL`) for simple lookups such as
   listing devices or showing a schema, and a reasoning tier (`gemini-3.1-pro-preview`,
   override with `MANTIS_PRO_MODEL`) for explanations, failures, and anything open-ended.
2. **Scoping.** On the reasoning tier Mantis first decides whether the question reports a
   failure. For a failure it lists competing hypotheses to test; for an informational
   question ("how does this test work?") it answers directly, without hypotheses.
3. **Investigation (Actor).** Mantis calls tools to read source, schemas, docs, and run
   artifacts, and drafts an answer grounded in what it read.
4. **Review (Critic, then Arbitrator).** On the reasoning tier a Critic checks the draft
   against the evidence and flags unsupported claims, and an Arbitrator produces the final
   answer. A triage answer ends with a **Hypothesis Resolution Audit** that states which
   hypothesis explains the failure and which were ruled out, and on what evidence.

Answers can include Mermaid or Graphviz diagrams.

## Triaging a failed test

The quickest path is the Workbench: select the site model and device, then use
**Diagnose with Mantis** on a failed test. From the command line, name the device, the test,
and the site model:

```bash
bin/mantis "Why did pointset_publish fail for AHU-1 in sites/udmi_site_model?"
```

Mantis reads the recorded run under
`<site_model>/out/devices/<device>/tests/<test>/` (`sequence.md`, `sequence.log`, message
traces). The triage report is written for the device's manufacturer as well as the lab: what
the device sent compared with what UDMI expects, which document and schema govern the
behavior, and what to change on the device. If the evidence shows the fault is not in the
device, the report says so. If no recorded run exists, Mantis says it is reasoning from
source alone.
