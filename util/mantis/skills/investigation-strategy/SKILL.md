---
name: investigation-strategy
description: Dynamic triage loop, codebase investigation tactics, Git regression hunting, and proposing actionable fixes.
---

# Dynamic Triage & Code Investigation Strategy

Effective debugging requires adapting your approach to the evidence. Navigate the phases below dynamically: fast-track to codebase research if the error is obvious, or escalate to differential analysis if the failure is subtle.

---

## 1. Core Test Semantics (Crucial Context)

Before analyzing any failure, you must understand the *intent* of the test.
*   **Intentional Failures & Corrupted Configs:** Tests like `broken_config` intentionally dispatch malformed payloads. Resulting schema violations are expected.
*   **Options & Expected Capability Skips:** Specific schema violations are expected to fail when Pubber is started with specific testing flags.
*   **Stable Outcome vs. Sync Timeout:** A timeout (e.g., `Failed waiting until config update synchronized`) is a real, unexpected failure, NOT an expected test output.

---

## 2. The Dynamic Investigation Loop

### Phase A: Stream Correlation & Symptom Discovery
Correlate active log streams by Timestamp and Transaction ID to identify the exact divergence point.

### Phase B: Fast-Track Codebase Research (For Explicit Errors)
**Trigger:** Phase A reveals an explicit stack trace or a specific schema mapping failure.
**Action:** Jump immediately to `grep_codebase` and `read_file_lines`.
*   **Search Static Signatures:** Strip out dynamic parameters (timestamps, IDs). Search only for static string components.
*   **Trace Variable Lineage:** Find where a failing variable is initialized, mutated, and serialized.
*   **Read Sufficient Context:** Do not just read 5 lines. Read the entire method and class-level variables to understand the state management.

### Phase C: Differential Sibling Analysis (For Silent Failures)
**Trigger:** Active logs end in a timeout with no explicit errors.
**Action:** Perform a chronological side-by-side comparison with the Reference Successful Run Details. Find exactly where the failed run diverged from the baseline.

### Phase D: Regression Hunting (For Stale Baselines)
**Trigger:** Codebase logic looks correct, but a previously passing test is now failing.
**Action:** Use Git tools for context and intent.
*   **Investigating "Weird" Logic:** Use `git log` or `git show` to read commit messages. They often reveal edge cases the original developer was trying to handle.
*   **Finding Recent Changes:** Use `git log` on target directories to identify recent PRs that altered shared caches or dependencies.

---

## 3. Constructing Actionable Code Fixes

When you have isolated the root cause, you must propose a concrete, drop-in code fix formatted as a standard unified diff block. Include:

1.  **The target file path** (e.g., `validator/src/.../SequenceBase.java`).
2.  **The logical context** explaining *why* this fix works.
3.  **The unified diff** showing what lines to remove (`-`) and insert (`+`), with sufficient unchanged context lines.

```diff
--- a/validator/src/main/java/com/google/daq/mqtt/sequencer/SequenceBase.java
+++ b/validator/src/main/java/com/google/daq/mqtt/sequencer/SequenceBase.java
@@ -450,6 +450,11 @@ public class SequenceBase {
     if (lastConfigTimestamp != null && config.timestamp.before(lastConfigTimestamp)) {
+        // Guard against out-of-order message delivery
+        warning("Ignoring update carrying timestamp older than last processed config.");
+        return;
+    }
```
