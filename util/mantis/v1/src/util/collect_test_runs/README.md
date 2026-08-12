# Mantis Test Run Collector (Stage 1) 🦗⚡

The **Test Run Collector** component is the first stage of Project Mantis. Its single responsibility is to collect test execution outcomes—either by running isolated sandbox execution loops locally, triggering new parallel dispatches on GitHub CI, or searching and importing completed past runs from a branch. It packages all raw results into standard support bundles.

It is executed via the launcher wrapper:

```bash
# Run local sandbox loops (default: 3 runs)
mantis/bin/collect_test_runs --mode local --target //mqtt/localhost --runs 5

# Dispatch fresh parallel GitHub CI dispatches
mantis/bin/collect_test_runs --mode ci --target //mqtt/localhost --runs 3

# Search and import past completed runs from GitHub
mantis/bin/collect_test_runs --mode ci_search --branch main --runs 5 --target //mqtt/localhost
```

All captured sharded run bundles are saved inside `out/mantis/` by default.

## Submodule Layout

* `__init__.py`: Package module marker.
* `main.py`: Orchestrator for local sandbox iterations (broker verification, prebuilds, sequencer loop, local file extraction) or GitHub API dispatches and zip bundle downloads.
