
## pointset_value_numeric (STABLE)

Check that numerical values in pointset payloads are reported as JSON numbers and not strings

1. Update config before receive a pointset event
    * Set `pointset.sample_rate_sec` = `10`
1. Wait for receive a pointset event
1. Check that received present_value for numeric point(s) [cooling_request_count, filter_differential_pressure_setpoint, filter_differential_pressure_sensor]

Test passed.
