---
name: sequencer-test-anatomy
description: Methodological playbook for inspecting Java sequencer test implementations, annotations, stage timeouts, and target specifications.
---

# Sequencer Test Anatomy & Code Inspection Playbook

## 1. Principle: Java Test Code is the Single Source of Truth
Every sequencer test is an authoritative JUnit test method implemented in Java under:
`validator/src/main/java/com/google/daq/mqtt/sequencer/sequences/`

Never assume or guess what a test expects. Use tools to read the Java test definition directly to understand its preconditions, `@Feature` stages, timeouts, and assertions.

## 2. Test Sequence Structure
A sequencer test class inherits from `SequenceBase` and defines `@Test` methods:
* **Annotations**:
  - `@Feature(stage = FeatureStage.<STAGE>, bucket = Bucket.<BUCKET>)`: Target maturity stage (`ALPHA`, `BETA`, `STABLE`, `PREVIEW`) and functional bucket (`SYSTEM`, `POINTSET`, `GATEWAY`, `DISCOVERY`).
  - `@Feature(nostate = true)`: Flags that the test operates in `nostate` mode, skipping initial state checks and requirements.
  - `@Summary("...")`: Summary description of the required device behavior and test expectations.
* **Test Flow**:
  1. Sets initial configuration via `deviceConfig.<subsystem> = ...`.
  2. Dispatches update via `untilTrue("condition", () -> ...)` or waits for expected events.
  3. Asserts outcomes via `assertTrue(...)`, `checkThat(...)`, `checkState(...)`.

## 3. Investigation Procedure
1. **Inspect Test Method**: Call `inspect_sequencer_test(test_name="<test_name>")` to extract:
   - File path and class name.
   - `@Feature` stage, bucket, and `@Summary` description.
   - Assertions, waiting conditions, and timeout configurations.
   - Complete Java source code body.
2. **Examine Preconditions**: If the test requires specific metadata (e.g., points defined in `metadata.json` or gateway configuration), verify the device's site model using `inspect_site_model(site_model="...", device_id="...")`.
3. **Resolve Target Endpoint**: If executing or reproducing the test, use canonical target specifications:
   - Local broker: `//mqtt/localhost:<port>` (requires active session).
   - Cloud ClearBlade: `//gbos/<project>/<namespace>`
   - Cloud Reflector: `//gref/<project>/<namespace>+<user>`
   - Cloud PubSub: `//pubsub/<project>/<namespace>+<user>`
4. **Dispatch Execution**: Call `run_sequencer_test(test_name="<test>", device_id="<device>", target_spec="<spec>")`.
