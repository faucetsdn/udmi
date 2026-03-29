[**UDMI**](../../../) / [**Docs**](../../) / [**Specs**](../) / [Sequences](#)

# Adding a Sequencer Test

This guide explains how to add a new sequencer test to the UDMI Java validator and what steps are necessary to ensure it passes Continuous Integration (CI).

## 1. Create the Test

Sequencer tests are located in `validator/src/main/java/com/google/daq/mqtt/sequencer/sequences/`. 
To create a new test, locate the appropriate sequence class (e.g., `SystemSequences.java`) or create a new one.

Add your test method, annotated with `@Test`, `@Feature`, and `@Summary`. For example, a trivial test:

```java
  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = ALPHA, bucket = SYSTEM)
  @Summary("Trivial test to check testing infrastructure")
  public void trivial_test() {
    checkThat("this is a trivial test", () -> true);
  }
```

*Note: The sequencer tests share the same JVM instance. Avoid using `static` variables for test state to prevent cross-test leakage and flaky behavior.*

## 2. Update Golden and Expected Files

Just adding the test is not sufficient. You need to update the expected outputs and golden files used by the CI testing infrastructure. 

### A. Update `docs/specs/sequences/generated.md`

This file documents all the tests. You must first generate sequence output logs by running the test:
```bash
bin/test_sequencer full $TARGET_PROJECT
```
To update the generated markdown, run:
```bash
bin/gencode_seq
```
*Note: regenerating the document locally can be tricky because it requires logs for all sequences to be generated. You can also manually insert the changes or rely on GitHub PR test results.*
This will append your new test to the documentation.

### B. Update `etc/sequencer.out`

This golden file contains the expected outputs for all tests. You must manually add your test's expected output or regenerate it by running the sequencer locally and verifying the output.

When manually adding an entry to `etc/sequencer.out`, **ensure the contents are sorted correctly** so they match the generated `out/sequencer.out` during testing. Use the following command to sort the file:
```bash
LC_ALL=C sort -k 3,4 -o etc/sequencer.out etc/sequencer.out
```

Example of a test entry:
```text
RESULT pass system.mode trivial_test ALPHA 10/10 Sequence complete
```

If you add, modify, or remove a test, this file must reflect those changes. 

### C. Verify with `bin/test_sequcheck`

To verify that your newly added output matches the tests, run:
```bash
bin/test_sequcheck
```
This command compares `out/sequencer.out` against `etc/sequencer.out`. It will fail if your test is not in the expected file or if the file isn't sorted correctly.

## 3. Dealing with Flaky Tests

When encountering unexpected test failures, you should **double-check them**, as flaky tests are common in complex integration test suites.

*   Use a **unique test name**. This makes it easy to trace any particular CI failure to your newly added test versus an existing, flaky test.
*   **Discovery tests:** If testing discovery events (e.g., nmap scans), use polling loops instead of hardcoded `time.sleep()` to prevent flaky test failures due to variable execution times.
*   **Timeouts:** If your test waits for a long-running operation (like a device restart or delayed connection), make sure to use `DEFAULT_LOOP_TIMEOUT` rather than the shorter `DEFAULT_WAIT_TIME` to prevent premature test timeouts.
*   **State Leakage:** As mentioned earlier, avoid modifying `static` fields. Config transactions leaking across test runs executing in the same JVM session will cause flaky failures in subsequent tests.

