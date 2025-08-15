## writeback_operation (ALPHA)

Tests the UDMI writeback functionality, covering fast, slow, and delayed scenarios based on the pubber's startup options.

### Default Behavior (Fast Operation)

When run without any options, this test validates a standard, successful writeback operation.
1.  **Clean state:** Ensures the target point has no `set_value`.
2.  **Trigger writeback:** Sends a config update to set the target point's value.
3.  **Wait for `applied`:** Waits for the device to report the point's `value_state` as `applied`.
4.  **Verify value:** Confirms the point's `present_value` matches the value that was set.

### `slowWrite` Option Behavior (Slow Operation)

When the pubber is launched with the `slowWrite` option, this test validates the device's ability to handle long-running operations by reporting an intermediate `updating` state.
1.  **Clean state:** Ensures the target point has no `set_value`.
2.  **Trigger writeback:** Sends a standard config update to set the point's value.
3.  **Wait for `updating`:** Waits for the device to report the `value_state` as `updating`.
4.  **Wait for `applied`:** After a delay, waits for the `value_state` to become `applied`.
5.  **Verify value:** Confirms the point's `present_value` matches the set value.

### `delayWrite` Option Behavior (Failed/Timeout Operation)

When the pubber is launched with the `delayWrite` option, this test validates that the system correctly handles a device that takes too long to respond. The pubber is configured to delay for 90 seconds, but the test will time out much sooner. The test is **skipped** after the timeout is confirmed, as this is the expected outcome for this scenario.

Test passed.
