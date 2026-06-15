---
name: best-effort-triage
description: Guidelines for formulating high-probability hypotheses when telemetry is incomplete.
---

# Best-Effort Search and Diagnostic Hypotheses

A senior debugging agent does not give up when a log stream is missing. Instead,
it uses the available evidence to form high-probability hypotheses. Your goal is
to provide maximum diagnostic value using whatever assets are present.

---

## 1. Weighing the Context Catalog

Before diving in, assess the availability of log sources. Do not abort your
analysis if global logs are missing; instead, adjust your investigative
strategy:

* **`sequence.log`:** This is your primary source of truth. If this is present,
  you have enough context to begin an investigation. Look for assertion
  failures, timeouts, and local stack traces.
* **`udmis.log`:** Crucial for tracing cloud routing and reflection. If absent,
  treat the cloud middleware as a "black box" and rely on what the Sequencer
  sent versus what it ultimately received.
* **`pubber.log` (represented as `Global_Pubber_Log: False`):** Useful for emulator
  state tracking. If absent (which is expected during physical device testing), the
  device under test is a **physical hardware black-box**. In this scenario, the `pubber/`
  codebase is **strictly off-limits**. Deduce the device's internal state entirely through the
  Sequencer's telemetry assertions, UDMIS logs, and physical device behavior. You can propose
  firmware fixes for the device manufacturer. It is not required to provide code modifications.

---

## 2. Formulating High-Probability Hypotheses

If log streams are incomplete, cut off abruptly, or lack explicit error traces,
you must still attempt to trace the code pathways that *could* lead to the final
known state.

1. **Isolate the Last Known Good State:** Identify the exact transaction ID or
   timestamp where the system was behaving correctly just before the failure.
2. **Investigate the Code Gap:** Use `grep_codebase` and `read_file_lines` to
   inspect the source code that governs the transition between the last known
   good state and the failure symptom. Look for edge cases, unhandled
   exceptions, or queue limits.
3. **State Your Assumptions:** Explicitly document the assumptions driving your
   hypothesis. (e.g., *"Assuming the device received the configuration payload
   because the Sequencer successfully published it to the broker..."*)

---

## 3. The Data Limitation Protocol

Absolute certainty is rare in asynchronous distributed systems. You should *
*only** completely abort an analysis if `sequence.log` is missing or totally
empty. Otherwise, you must always provide a best-effort Root Cause Analysis (
RCA).

If the telemetry is insufficient to mathematically guarantee the root cause, do
not panic. Simply structure your **Proposed Code Fix** section to reflect this
uncertainty:

### A. The Primary Hypothesis

Present your most logical, highest-probability explanation for the failure based
strictly on the codebase logic and the logs you *do* have. If possible, propose
the code fix that addresses this hypothesis.

### B. Missing Telemetry Context

Explicitly state what specific data was missing that prevented a 100% definitive
conclusion (e.g., *"Global `udmis.log` was not provided, meaning we cannot
verify if the dynamic routing cache was overwritten during the test window."*).

### C. Recommendations for Next Run

Give the human developer explicit instructions on what telemetry to enable, or
what specific variables to log, for the next test execution to definitively
prove or disprove your hypothesis.