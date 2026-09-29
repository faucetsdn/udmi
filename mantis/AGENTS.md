# Mantis Agent Instructions & Engineering Standards

This document establishes the mandatory engineering standards, architectural invariants, and operational guidelines for agents developing, modifying, or operating Mantis.

---

## 1. Single Source of Truth (SSoT) Mandate

- **No Hardcoded Domain Knowledge**: Never embed static schema definitions, state transition tables, error signature regex dictionaries, or device lists in prompts, skills, or tools.
- **Dynamic Codebase Traversal**: When answering questions or diagnosing failures, always inspect the codebase dynamically:
  - JSON schemas: `schema/*.json` (via `inspect_udmi_schema`).
  - Specifications: `docs/specs/`, `docs/messages/` (via `locate_udmi_doc` or `read_udmi_file`).
  - Java test implementations: `validator/src/main/java/com/google/daq/mqtt/sequencer/sequences/*.java` (via `inspect_sequencer_test`).
  - Message synchronization logic: `validator/.../SequenceBase.java`.
- **Navigational Skills**: Skills in `mantis/skills/` must remain pure navigational playbooks that instruct the agent *where* and *how* to investigate the codebase, rather than hardcoding facts that may drift.

---

## 2. Tripartite Cognitive Loop (`Actor -> Critic -> Arbitrator`)

`MantisAgent.classify_intent_tier` routes each prompt to one model tier for the whole run. Flash-tier prompts (simple lookups: list devices, show a schema, slice a log) are answered by the Actor alone. All complex reasoning, explanations, diagnostic triage, and failure analysis are routed to the Pro tier (`gemini-3.1-pro-preview`), which runs a scoping turn and then the Tripartite loop:
0. **Scoping**:
   - Classifies the prompt. A failure gets competing hypotheses that the Arbitrator's `## Hypothesis Resolution Audit` must resolve; a prompt that reports no failure (`NOT_A_FAILURE`) runs in informational mode without hypotheses.
1. **Actor Phase**:
   - Executes exploratory tool calls (codebase searches, schema lookups, log slicing, timeline extraction) on the routed tier.
   - Collects empirical evidence and drafts the answer.
2. **Critic Phase**:
   - Performs an adversarial audit on the Pro tier (`gemini-3.1-pro-preview`).
   - Assesses claims against the Single Source of Truth (codebase, schemas, logs).
   - Flags unverified assumptions, hallucinations, or contradictions with UDMI specifications.
3. **Arbitrator Phase**:
   - Reconciles discrepancies between the Actor and Critic on the Pro tier (`gemini-3.1-pro-preview`).
   - Synthesizes the final verified response.
   - Autonomously determines if visual aids (Graphviz DOT or Mermaid) enhance clarity and includes them if appropriate.

---

## 3. Anti-Cheating & Golden Baseline Integrity

- **Golden Files are Invariant**: Files under `etc/` (such as `etc/validator.out`, `etc/schema_nostate.out`) represent baseline golden expectations.
- **Never Update Golden Files to Pass Tests**: Updating golden files simply to make failing tests pass without a documented schema or version upgrade is strictly prohibited.
- **Verification Rule**: Use `verify_golden_baseline` to validate test outputs and ensure no events were dropped or truncated.

---

## 4. Environment & Execution Standards

- **Non-Root Execution**: Non-root execution is the default across all scripts. Local test infrastructure uses unprivileged port blocks (e.g., `//mqtt/localhost:18833`). Never require or assume `sudo`.
- **State Isolation**: Each test session runs in its own directory under `var/instances/<session>` with independent tmux windows (`main`, `dut`, `sequencer`, `butler`, `validator`).
- **Cancellation**: Every long-running phase must call `check_cancel` at its boundary so `run(..., cancel_event=...)` can stop the agent (the Workbench Stop button depends on this).
- **Fail Fast & No Silent Fallbacks**: If a prerequisite, file path, or schema is missing or invalid, fail immediately with an explicit, actionable error. Do not silently fall back to defaults.
- **No Git Commits**: Leave all changes uncommitted unless explicitly instructed by the user to commit. Never amend commits or rewrite git history.

---

## 5. Verification Protocol

Before declaring any Mantis enhancement or fix complete:
1. Run the Mantis test suite:
   ```bash
   bin/test_mantis
   ```
2. Run Stage 1 unit tests:
   ```bash
   bin/run_tests all_tests
   ```
3. Ensure all tests pass with zero regressions.
