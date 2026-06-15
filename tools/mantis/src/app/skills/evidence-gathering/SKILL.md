---
name: evidence-gathering
description: Strategies for deep codebase investigation, utilizing Git for context, and proposing actionable fixes.
---

# Code Investigation & Evidence Gathering Strategies

As an advanced debugging agent, your goal is to bridge the gap between a
symptom (a log line or a timeout) and the underlying state machine logic. Use
your tools dynamically to build a complete picture of the failure.

---

## 1. Codebase Search Tactics

When moving from logs to source code, avoid shallow searches. Use your codebase
tools to map the entire lifecycle of the failing transaction.

* **Search Static Signatures:** When grepping for log lines, strip out dynamic
  parameters (timestamps, IDs, hash values). Search only for the static string
  components or the exact Exception class names.
* **Trace Variable Lineage:** If a variable (like `last_start` or
  `expected_version`) evaluates to an unexpected value leading to a failure, do
  not stop at the assertion. Use `grep_codebase` to find where that variable is
  initialized, mutated, and serialized.
* **Read Sufficient Context:** When using `read_file_lines`, do not just read
  the 5 lines around the error. Read the entire method and class-level
  variables (requesting 100-200 lines if necessary) to understand the class's
  state management, threading model, and error handling.

---

## 2. Using Git for Context and Intent

Git is not just for finding recent regressions; it is a critical tool for
understanding *why* code was written a certain way. Use `git_read_operations` to
tap into historical developer intent.

* **Investigating "Weird" Logic:** If you find a block of code that seems
  counter-intuitive, race-condition prone, or overly complex, use `git log` or
  `git show` on that specific file/line. Reading the commit message that
  introduced the logic often reveals edge cases the original developer was
  trying to handle.
* **Regression Hunting:** If the code logic appears sound but a previously
  passing test is now failing, use `git log` on the target directories (
  `udmis/src/`, `validator/src/`) to identify recent changes. Look for commits
  that altered shared caches, updated dependency versions, or modified transport
  routing.
* **Comparing States:** Use `git diff` to see what changed between the current
  working tree and a stable past state if you suspect a very recent,
  uncommitted, or freshly merged bug.

---

## 3. Constructing Actionable Code Fixes

When you have isolated the root cause, you must propose a concrete, drop-in code
fix. Do not just describe the fix conceptually.

Provide the exact code modification formatted as a standard unified diff block.
Your proposed fix must include:

1. **The target file path:** (e.g.,
   `validator/src/main/java/.../SequenceBase.java`).
2. **The logical context:** A brief explanation of *why* this fix works (e.g., "
   This adds a temporal guardrail to prevent older Pub/Sub messages from
   overwriting newer state").
3. **The unified diff:** Showing exactly what lines to remove (`-`) and what
   lines to insert (`+`), surrounded by sufficient unchanged context lines so a
   developer can easily locate the insertion point.

### Formatting Example:

```diff
--- a/validator/src/main/java/com/google/daq/mqtt/sequencer/SequenceBase.java
+++ b/validator/src/main/java/com/google/daq/mqtt/sequencer/SequenceBase.java
@@ -450,6 +450,11 @@ public class SequenceBase {
     if (lastConfigTimestamp != null && config.timestamp.before(lastConfigTimestamp)) {
+        // Guard against out-of-order message delivery
+        warning("Ignoring update carrying timestamp older than last processed config.");
+        return;
+    }