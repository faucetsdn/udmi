---
name: best-effort-triage
description: Guidelines for conducting a best-effort search and triggering the sufficiency guardrail.
---

# Best-Effort Search and Diagnostic Sufficiency Guardrail

A key quality of a robust debugging agent is the ability to perform a thorough, best-effort search using whatever assets are present, while cleanly reporting when the provided telemetry is mathematically insufficient to guarantee a root-cause diagnosis.

---

## 1. The Sufficiency Checklist (Context Catalog)

Before declaring a diagnostic conclusion, perform a rigorous check on the availability of log sources in the provided support package directory.

| Log Stream | Role in Sufficiency | If Missing... |
| :--- | :--- | :--- |
| **`sequence.log`** | **CRITICAL** | Cannot diagnose. If this file is missing or completely empty, abort immediately. |
| **`udmis.log`** | **HIGHLY IMPORTANT** | If absent, you cannot trace cloud routing or cloud-side exceptions. You must treat the cloud plane as a "black box". |
| **`pubber.log`** | **USEFUL** | If absent, check if the device under test is physical or virtual. If it is physical, missing this log is expected (black-box device mode). If virtual, missing this is a defect in the run collection. |

---

## 2. Best-Effort Search Plan

When log sources are incomplete, do not give up immediately. Follow these steps to maximize diagnostic output:

1.  **Examine `sequence.log` for Stack Traces:** Even without global `udmis.log`, the Sequencer log often captures local stack traces or connection error codes returned from remote channels.
2.  **Check JUnit Assertions:** Read the exact assertion error line at the bottom of `sequence.log`. (e.g., `Expected state system.last_start to sync with config`).
3.  **Compare with Golden Files:** Use `grep_codebase` to inspect `etc/test_itemized.out` and read what golden comparison values are expected for this test case.
4.  **State Assumptions Clearly:** In your report, explicitly write down your assumptions. For example: *"Assuming the UDMIS routing plane resolved to clearblade-iot-core since UDMIS logs are absent."*

---

## 3. The "Insufficient Data" Guardrail

If the local logs, git history, and code logic are **insufficient** to isolate the exact breakpoint (e.g., the sequence log abruptly stops with no explanation, or global logs for UDMIS are missing during a dynamic routing error), you **must** trigger the Insufficient Data guardrail.

### 1. The String Trigger
You MUST start the **Breakpoint Summary blockquote** or the **Proposed Code Fix section** with this exact header:
```
⚠️ INSUFFICIENT DATA TO TRACE ROOT CAUSE
```
This exact string is parsed by triage engines to trigger alerts, and signals to the user that more telemetry must be collected.

### 2. Formulating the "Insufficient Data" Report
When this guardrail is triggered, your report must cover:
1.  **What we *do* know:** A concise summary of the last known healthy events recorded before the logs went blind.
2.  **Exactly what log streams are missing:** (e.g., *"Global `udmis.log` is missing for the time window `12:15:41` - `12:16:41`"* or *" Mosquitto traffic capture was not collected"*).
3.  **Clear recommendations for the next run:** Give the developer explicit instructions on how to configure the next execution to capture the missing logs (e.g., *"Ensure `bin/collect_stats` is executed with global logging enabled"* or *"Check if Mosquitto daemon has logging levels set to debug"*).
