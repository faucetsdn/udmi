# Mantis Stability Evaluator (Stage 2) 🦗🔍

The **Stability Evaluator** component is the second stage of Project Mantis. Its single responsibility is pure metric analysis—completely decoupled from test executions, broker checks, or active run logic.

It ingests a directory containing raw sharded runs or `.zip`/`.tgz` support bundles (captured by Stage 1), processes them, compares normalized outcomes against golden baselines (e.g., `etc/sequencer.out`), computes flakiness scores, compiles the failure manifest, and generates stabilization reports.

It is executed via the launcher:

```bash
mantis/bin/evaluate_stability \
  --bundles-dir out/mantis/<target_folder>/ \
  --target //mqtt/localhost
```

All reports, manifest files, and metrics caches are saved directly under the target `--bundles-dir` directory.

## Submodule Layout

* `__init__.py`: Package module marker.
* `main.py`: Main orchestrator. Extract zips/tgz files via the `support_process` wrapper and coordinates the log analysis sweeps.
* `analyzer.py`: Parses sharded test results, normalizes dynamic ISO timestamps/pipeline details, and maps outcomes against golden baselines using occurrence-based indexing.
* `reporter.py`: Formats flakiness Markdown tables, serializes metrics caches, generates the triage manifest JSON, and renders chronological comparative Evolution reports when `--compare` / `-c` is specified.
