
## pointset_value_multistate (STABLE)

Check that multi-state values in pointset payloads match configured values keys

1. Update config before receive a pointset event
    * Set `pointset.sample_rate_sec` = `10`
1. Wait for receive a pointset event
1. Check that received present_value for multi-state point(s) [occupancy_mode, supply_fan_run_status]

Test passed.
