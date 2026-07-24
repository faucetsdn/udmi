# 🤖 AI Agent Guidelines for `ui/`

This document provides essential instructions, architectural guidelines, and verification rules for AI agents (and human developers) working on the UDMI Workbench UI codebase.

---

## 🎯 Primary Directives

1. **Specifications as Source of Truth**:
   - The [`ui/spec/`](./spec/README.md) directory contains the canonical, technology-agnostic specifications for the UI architecture, visual tokens, data contracts, and tool requirements.
   - Always consult [`ui/spec/`](./spec/README.md) before implementing new features or making architectural changes.
   - Do NOT pollute specification documents in `ui/spec/` with framework-specific code or CSS stylesheets; specs must remain implementation-agnostic.

2. **Directory Layout**:
   - All frontend application source code resides in [`ui/src/`](./src/):
     - `ui/src/index.html`, `main.js`, `style.css`: Parent Host Shell orchestrator.
     - `ui/src/shared/`: Shared design system theme and reusable components.
     - `ui/src/sequencer/`: Sequencer compliance test execution micro-frontend tool.
     - `ui/src/mantis/`: Mantis AI triage & debugger micro-frontend tool.
   - Backend API server and static asset server: [`ui/server.py`](./server.py).
   - Python unit tests: [`ui/tests/test_server.py`](./tests/test_server.py).

---

## 🏗️ Architectural Rules

1. **Micro-Frontend Sandboxing**:
   - Every tool view MUST be isolated within its own sandbox (iframe context).
   - Style definitions inside a micro-frontend MUST NOT bleed into neighboring tools or the parent shell.
   - Failures or script exceptions inside one tool MUST NOT break or crash the parent shell orchestrator.

2. **State Synchronization (PostMessage API)**:
   - Global state changes (such as the active `siteModel` path) are managed by the parent shell (`ui/src/main.js`) and broadcast downstream to child tools via the HTML5 `postMessage` API using the `udmi_state_change` message type.
   - Child tool views MUST subscribe to window message events and update their local state dynamically without requiring iframe reloads.
   - Late-bound push: The parent shell pushes state to child tool views immediately upon their `load` event.

3. **Backend API Integration**:
   - Do NOT attempt to run local shell subprocesses directly from frontend scripts.
   - Route all device discovery, file listing, test execution, and log streaming through the backend endpoints specified in [`ui/spec/core/03-data-and-api-contracts.md`](./spec/core/03-data-and-api-contracts.md) provided by `ui/server.py`.

---

## 🧪 Verification & Testing Protocol

Before marking any task as complete when working on `ui/`:

1. **Run Workbench Server Unit Tests**:
   ```bash
   bin/test_workbench
   ```
   Ensure all Python server tests in `ui/tests/test_server.py` pass without errors.

2. **Verify Shell Launcher**:
   ```bash
   bin/workbench
   ```
   Confirm that the backend server spins up cleanly on port 8080 and serves `http://localhost:8080/ui/src/index.html`.

---

## 🚀 UI Automation Scripts (`ui/bin/`)

- **`ui/bin/build_new`**: Creates an isolated git worktree, clears disposable UI files (`ui/src/` and `ui/spec/impl/`), prompts for a visual vibe or picks a random theme, and invokes the Gemini AI CLI to rebuild the UI from scratch without touching HEAD.
- **`ui/bin/promote`**: Promotes a generated UI implementation from an isolated worktree branch into workspace HEAD.
- **`ui/bin/iterate`**: Refines an existing UI implementation based on specific user instructions using Gemini AI.
