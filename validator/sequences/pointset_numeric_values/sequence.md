
## pointset_numeric_values (STABLE)

Check that numerical values in pointset payloads are reported as JSON numbers and not strings

1. Update config before receive a pointset event
    * Set `pointset.sample_rate_sec` = `10`
1. Wait for receive a pointset event

Test passed.
