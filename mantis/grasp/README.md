# Mantis Grasp (Stage 2) 🦗🔍

The **Grasp** component is the chronological second stage of Project Mantis. Its single responsibility is pure metric analysis—decoupled from execution environments, loops, mosquitto checks, or process controls.

It ingests a folder of captured raw zip packages, extracts them, compares raw results against golden baselines, aggregates stability ratios, and outputs Markdown reports.

It is executed via the wrapper launcher:
```bash
mantis/bin/grasp --target //mqtt/localhost --bundles-dir <path_to_captured_bundles>
```

## Submodule Files
- `__init__.py`: Package module marker.
- `main.py`: Parses arguments and aggregates run analyses.
- `analyzer.py`: Compares test lines against golden expectation arrays using sequence occurrence matching and regex timestamp normalizations.
- `reporter.py`: Renders phase metrics tables and comparative deltas between runs.
