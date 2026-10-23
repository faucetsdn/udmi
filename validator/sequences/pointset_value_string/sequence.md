
## pointset_value_string (STABLE)

Check that string values in pointset payloads are reported as JSON strings

1. Update config before receive a pointset event
    * Set `pointset.sample_rate_sec` = `10`
1. Wait for receive a pointset event
1. Check that received present_value for string point(s) [active_fault_code]

Test passed.
