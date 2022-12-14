
## system_mode_restart

Restart and connect to same endpoint and expect it returns.

1. Wait for last_start is not zero
1. Update config before deviceState.system.mode == ACTIVE:
    * Add `system.mode` = `active`
1. Wait for deviceState.system.mode == ACTIVE
1. Update config before deviceState.system.mode == INITIAL:
    * Set `system.mode` = `restart`
1. Wait for deviceState.system.mode == INITIAL
1. Update config before deviceState.system.mode == ACTIVE:
    * Set `system.mode` = `active`
1. Wait for deviceState.system.mode == ACTIVE
1. Wait for last_config is newer than previous last_config after abort
1. Wait for last_start is newer than previous last_start
