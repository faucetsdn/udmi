
## system_last_update (STABLE)

Check that last_update state is correctly set in response to a config update.

1. Wait until state last_config matches config timestamp
1. Wait until state update complete
1. Force config update to trigger another config update
1. Wait until state last_config matches config timestamp
1. Wait until state update complete
1. Force config update to trigger another config update
1. Wait until state last_config matches config timestamp
1. Wait until state update complete
