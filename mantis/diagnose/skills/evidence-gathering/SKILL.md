---
name: evidence-gathering
description: How to search the codebase, run git queries, and draft proposed code fixes.
---

# Code Investigation & Evidence Gathering Skill File

This guide instructs a debugging agent on how to locate anomalous code pathways, extract evidence from git records, and synthesize exact, drop-in code fixes.

---

## 1. Tracing Logs to Source Code

When logs reveal an anomaly (e.g., an error state or an unexpected status), locate the source code block that generated that log statement.

### 1. Code Search Rules:
*   **Strip dynamic parameters:** Do not search for raw lines (which contain dates, IDs, or numbers). Isolate only the static string parts.
    *   *Raw log:* `ReflectProcessor Propagating message RC:18d9b7.00000003: ValidationState`
    *   *Search query:* `"Propagating message"` or `"ReflectProcessor"`
*   **Target directories first:** Direct your codebase searches to the target Java directories to save search overhead:
    *   `validator/src/` (Sequencer)
    *   `udmis/src/` (UDMIS service pod)
    *   `pubber/src/` (Pubber emulator)

### 2. Execution Tool Commands (Inside CodeBase):
Use your `grep_codebase` tool to isolate the file and line numbers:
```python
grep_codebase(pattern="Propagating message")
```

Once grep returns the target file and line number, use `read_file_lines` to inspect the surrounding code logic block (up to 100 lines before and after the line):
```python
read_file_lines(filepath="udmis/src/main/java/com/google/bos/udmi/service/processor/ReflectProcessor.java", start_line=120, end_line=200)
```

---

## 2. Investigating Regressions via Git History

If the code logic looks correct but recently failed in staging or a new commit broke stability, utilize Git logs to find the regression.

*   **Rule:** Only use git queries to investigate regressions when you suspect a recent commit introduced a bug.
*   **Commands to execute using `git_read_operations`:**
    1.  **List recent commits on a path:**
        ```python
        git_read_operations(repo_path=".", command="log", args=["-n", "10", "--oneline", "--", "udmis/src/"])
        ```
    2.  **View the exact changes introduced by a suspect commit:**
        ```python
        git_read_operations(repo_path=".", command="show", args=["<commit_hash>", "--", "udmis/src/"])
        ```
    3.  **View changes between the current working tree and another branch or revision:**
        ```python
        git_read_operations(repo_path=".", command="diff", args=["HEAD~3", "HEAD", "--", "validator/src/"])
        ```

---

## 3. Constructing Clean Unified Diffs for Proposing Fixes

When proposing a bug fix or a concurrency guard rail, do not just describe it in English. You must provide the exact code modification formatted as a unified diff or code block, including:
1.  **The exact absolute or relative file path** (e.g., `validator/src/main/java/com/google/daq/mqtt/sequencer/SequenceBase.java`).
2.  **The approximate line range** (e.g., `Lines 450 - 462`).
3.  **A standard unified diff block** showing exactly what lines to delete (`-`) and what lines to insert (`+`).

### Example Format:
```diff
diff --git a/validator/src/main/java/com/google/daq/mqtt/sequencer/SequenceBase.java b/validator/src/main/java/com/google/daq/mqtt/sequencer/SequenceBase.java
index abc1234..def5678 100644
--- a/validator/src/main/java/com/google/daq/mqtt/sequencer/SequenceBase.java
+++ b/validator/src/main/java/com/google/daq/mqtt/sequencer/SequenceBase.java
@@ -450,6 +450,12 @@ public class SequenceBase {
     if (lastConfigTimestamp != null && config.timestamp.before(lastConfigTimestamp)) {
+        // Guard against out-of-order Pub/Sub message regression
+        warning("Ignoring config update reflection carrying timestamp older than last processed config.");
+        return;
+    }
```

---

## 4. Formatting Chronological Event Timelines

When presenting log evidence to a developer, organize the correlated events in a clean chronological table. 

### Timeline Assembly Protocol
To ensure absolute completeness, you must build the table strictly using the following sequence:
1. **Test Start:** First capture the timestamp when the test case execution was initialized.
2. **Iteratively trace each Sequencer transaction:**
   * **Action:** Capture the timestamp of any action taken by the Sequencer (e.g. publishing a config with `RC:xxxxxx.xxxxxxxx`).
   * **UDMIS Reception:** Trace if the corresponding transaction reached the UDMIS routing pod and print the log showing where UDMIS processed it. If UDMIS logs do not record processing this transaction ID, explicitly capture UDMIS processing as missing.
   * **Pubber Reaction (If applicable):** Trace if Pubber received the transaction and how it acted (e.g. connection status, applying config, publishing updated state echo).
   * **Result/Response:** Trace if the state/telemetry packet or echo was successfully received back by the Sequencer, or identify any issues/timeouts observed.
3. **Test Stop:** Capture the final timestamp when the test stopped and failed (e.g. notice `Ending test...` or sync timeout).

The table must map:
1.  **Timestamp (UTC):** Accurate to the millisecond level if available.
2.  **Source:** The specific component (Sequencer, UDMIS, Mosquitto, or Pubber).
3.  **Log Message / Event:** The precise log snippet, sanitized of irrelevant noise.
4.  **Significance:** Technical explanation of why this event is relevant to the failure sequence.
