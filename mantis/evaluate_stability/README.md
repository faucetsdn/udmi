# Mantis Stability Evaluator (Stage 2) 🦗🔍

The **Stability Evaluator** component is the chronological second stage of Project Mantis. Its single responsibility is pure metric analysis—completely decoupled from test executions, mosquitto broker checks, or active run logic.

It ingests a folder containing raw `.zip` or `.tgz` support bundles (captured by Stage 1), processes them, compares normalized results against golden baselines (e.g., `etc/sequencer.out`), computes flakiness scores, and outputs comparative stability reports.

It is executed via the wrapper launcher:
```bash
mantis/bin/evaluate_stability \
  --target //mqtt/localhost \
  --bundles-dir mantis/out/test_bundles/<target_folder>/
```

All reports and metrics are saved self-containedly under `mantis/out/`.

## Submodule Files
- `__init__.py`: Package module marker.
- `main.py`: Coordinate run consolidation and aggregation.
- `analyzer.py`: Parses results, strips dynamic ISO timestamps, and compares results against Golden baselines using occurrence-based indexing.
- `reporter.py`: Formats Markdown metrics tables, serializes JSON metrics, and generates before/after comparative deltas.
