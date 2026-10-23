
## pointset_value_boolean (STABLE)

Check that boolean values in pointset payloads are reported as JSON booleans

1. Update config before receive a pointset event
    * Set `pointset.sample_rate_sec` = `10`
1. Wait for receive a pointset event
1. Check that received present_value for boolean point(s) [filter_alarm_pressure_status]

Test passed.
