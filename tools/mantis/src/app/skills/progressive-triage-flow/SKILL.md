---
name: progressive-triage-flow
description: Dynamic diagnostic strategies and core test semantics for isolating failures efficiently.
---

# Dynamic Triage Strategy & Test Semantics

Effective debugging requires adapting your approach to the evidence. Do not
follow a rigid checklist blindly. Navigate the phases below dynamically:
fast-track to codebase research if the error is obvious, or escalate to
differential analysis if the failure is subtle.

---

## 1. Core Test Semantics (Crucial Context)

Before analyzing any failure, you must understand the *intent* of the test. Not
all exceptions are bugs.

* **Intentional Failures & Corrupted Configs:**
  Tests like `broken_config` or `extra_config` intentionally dispatch malformed
  or out-of-schema payloads. The resulting `JsonParseException`, `Level.ERROR`
  logs, and schema violations in the device/middleware are **fully expected**.
  Do not treat these expected exceptions as infrastructural bugs.
* **Options & Expected Capability Skips:**
  Pubber is often started with specific testing flags (e.g., `badLevel`,
  `badCategory`). Under these options, specific schema violations and capability
  checks are **expected to fail**. These are recorded as expected skips in the
  golden baseline files.
* **Stable Outcome vs. Sync Timeout:**
  If a test terminates with `STABLE 0/10` due to a timeout (e.g.,
  `Failed waiting until config update synchronized`), this is a **real,
  unexpected failure** (such as a deadlocked channel, race condition, or dropped
  packet), NOT an expected test output.

---

## 2. The Dynamic Investigation Loop

Apply these investigative phases based on the symptoms you uncover in the logs.

### Phase A: Stream Correlation & Symptom Discovery (Always Start Here)

* Correlate the active log streams (Sequencer, UDMIS, Pubber) by Timestamp and
  Transaction ID.
* Identify the exact divergence point: Did a packet drop? Did a wait loop
  timeout? Did an exception halt the thread?

### Phase B: Fast-Track Codebase Research (For Explicit Errors)

* **Trigger:** If Phase A reveals an explicit stack trace, a
  `NullPointerException`, a clear logic error, or a specific schema mapping
  failure.
* **Action:** Skip sibling comparisons. Jump immediately to `grep_codebase` and
  `read_file_lines` to inspect the failing class and propose a fix.

### Phase C: Differential Sibling Analysis (For Silent Failures & Timeouts)

* **Trigger:** If the active logs end in a timeout with no explicit errors, or
  if you suspect a race condition / dropped packet.
* **Action:** Look at the `Reference Successful Run Details`. Perform a
  chronological side-by-side comparison. Find exactly where the failed run
  diverged from the baseline. Use this to pinpoint which component dropped the
  ball.

### Phase D: Regression Hunting (For Stale Baselines)

* **Trigger:** If the codebase logic looks correct, but a previously passing
  test is now failing across the board.
* **Action:** Use `git_read_operations` to inspect recent commits on the target
  path. Look for unhandled edge cases introduced by recent PRs.
* **Exception:** If only a single run is under triage and NO successful baseline
  run exists, skip Git history checks entirely. Focus on the available logs.

---

## 3. Formulating the Conclusion

Once you have dynamically navigated the evidence to isolate the breakpoint:

1. Validate your hypothesis against the actual Java code logic.
2. Draft a precise root cause summary.
3. Propose your drop-in unified diff code fix (as detailed in
   `evidence-gathering`).