# Mantis Capture (Stage 1) 🦗⚡

The **Capture** component is the chronological first stage of Project Mantis. Its single responsibility is to trigger parallel CI dispatches on GitHub Actions, or isolated execution test loops locally inside your sandbox, packaging raw consolidated results into standard zip support packages.

It is executed via the wrapper launcher:
```bash
# Trigger GitHub CI workflows (concurrency-safe default: 3 iterations)
mantis/bin/capture --target //mqtt/localhost

# Trigger Local Sandbox Loops (default: 10 iterations)
mantis/bin/capture --local --target //mqtt/localhost
```

## Submodule Files
- `__init__.py`: Package module marker.
- `main.py`: Execution orchestrator (MOSQUITTO checks, builds, loops, PIDs cleanup, dispatches, and standard `zipfile` bundler).
