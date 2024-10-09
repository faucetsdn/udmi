
## system_last_update (STABLE)

Check that last_update state is correctly set in response to a config update.

1. Wait for config sync
1. Wait for state last_config matches config timestamp
1. Wait for state update complete
1. Wait for config sync
1. Force config update to trigger another config update
1. Wait for state last_config matches config timestamp
1. Wait for state update complete
1. Wait for config sync
1. Force config update to trigger another config update
1. Wait for state last_config matches config timestamp
1. Wait for state update complete
