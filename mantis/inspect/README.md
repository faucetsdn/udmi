# Mantis Inspect (Stage 3) 🦗👁️

The **Inspect** component is the chronological third stage of Project Mantis. It serves as an AI-powered diagnostic triage agent that investigates failed sequencer executions by correlating distributed log streams, mining git histories for references, and codebase inspection using Gemini registered tools.

It is executed via the wrapper launcher:
```bash
export GEMINI_API_KEY="your_api_key"
mantis/bin/inspect --target //mqtt/localhost --run-dir <path_to_iteration_backup>
```

## Submodule Files
- `__init__.py`: Package module marker.
- `main.py`: Performs automated failure sweep scans, slices global logs using execution timebounds, and writes nested folder reports under `out/mantis/<project>/<site>/<device>/<test>/triage_analysis.md`.
- `agent.py`: Sets standard `google.genai` sessions and triggers `gemini-2.5-pro` content generations under low temperature.
- `tools.py`: Exposes codebase search `grep_codebase`, chunk reader `read_file_lines`, and read-only git log utility `git_read_operations` directly to the AI.
