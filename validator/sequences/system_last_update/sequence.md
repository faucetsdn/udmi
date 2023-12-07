
## system_last_update (STABLE)

Check that last_update state is correctly set in response to a config update.

1. Wait for state last_config matches first config timestamp
1. Force config update to trigger another config update
1. Wait for state last_config matches new config timestamp
