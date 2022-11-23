
## pointset_publish_interval

test sample rate and sample limit sec

1. Update config before receive at least 3 pointset events:
    * Add `pointset.sample_rate_sec` = `8`
    * Add `pointset.sample_limit_sec` = `5`
1. Wait for receive at least 3 pointset events
1. Check that time period between successive pointset events is between 5 and 8 seconds seconds
1. Update config before receive at least 3 pointset events:
    * Set `pointset.sample_rate_sec` = `15`
    * Set `pointset.sample_limit_sec` = `12`
1. Wait for receive at least 3 pointset events
1. Check that time period between successive pointset events is between 12 and 15 seconds seconds
