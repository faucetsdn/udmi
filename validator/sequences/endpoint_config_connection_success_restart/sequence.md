
## endpoint_config_connection_success_restart

Restart and connect to same endpoint and expect it returns.

1. Wait for last_start is not zero
1. Update config:
    * Add `system.mode` = `active`
1. Wait for deviceState.system.mode == ACTIVE
1. Update config:
    * Set `system.mode` = `restart`
1. Wait for deviceState.system.mode == INITIAL
1. Update config:
    * Set `system.mode` = `active`
1. Wait for deviceState.system.mode == ACTIVE
1. Wait for last_config is newer than previous last_config
1. Wait for last_start is newer than previous last_start 2022-11-15T18:50:10Z
