# Mantis Statistics Collector (Stage 1) 🦗⚡

The **Statistics Collector** component is the chronological first stage of Project Mantis. Its single responsibility is to collect test execution outcomes—either by triggering new parallel dispatches on GitHub CI, searching and importing completed past runs from a branch, or running isolated sandbox execution loops locally. It packages all raw results into standard support bundles (`.tgz` locally via `bin/support` or `.zip` downloaded from GitHub).

It is executed via the wrapper launcher:
```bash
# Trigger new GitHub CI dispatches (concurrency-safe default: 3 iterations)
mantis/bin/collect_stats --target //mqtt/localhost

# Search and import past completed runs from GitHub (e.g. latest 5 on main)
mantis/bin/collect_stats --github-search --branch main --limit 5 --target //mqtt/localhost

# Trigger Local Sandbox Loops (default: 10 iterations)
mantis/bin/collect_stats --local --target //mqtt/localhost
```

All captured bundles are saved inside `mantis/out/test_bundles/` by default.

## Submodule Files
- `__init__.py`: Package module marker.
- `main.py`: Execution orchestrator (broker checks, prebuilds, execution loops, parallel CI dispatches, API search queries, and support bundler).
