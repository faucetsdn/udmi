## writeback_slow (ALPHA)

Tests a slow writeback operation, ensuring the device reports an intermediate `updating` state before the operation completes. This test requires the `pubber` to be launched with a `slowDevice` option.

1.  **Clean state:** The test sends a config update to ensure the target point has no `set_value`.
2.  **Trigger writeback:** The test sends a standard config update to set the target point's value.
3.  **Wait for `updating`:** The test waits for the device to report the point's `value_state` as `updating`.
4.  **Wait for `applied`:** After the pre-configured 90-second delay, the test waits for the `value_state` to become `applied`.
5.  **Verify value:** The test confirms the point's `present_value` matches the value that was set.

Test passed.
