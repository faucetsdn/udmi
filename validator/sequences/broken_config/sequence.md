
## broken_config

Check that the device correctly handles a broken (non-json) config message.

1. Update config before no interesting status:
    * Set `system.min_loglevel` = `100`
1. Wait for no interesting status
1. Wait for state synchronized
1. Check that initial stable_config matches last_config
1. Wait for log category `system.config.apply` level `NOTICE`
1. Wait for log category `system.config.receive` level `DEBUG`
1. Wait for has interesting status
1. Wait for log category `system.config.parse` level `ERROR`
1. Force reset config
1. Update config before no interesting status:
    * Add `system.last_start` = `device reported`
    * Set `system.min_loglevel` = `100`
1. Wait for no interesting status
1. Wait for last_config updated
1. Wait for log category `system.config.apply` level `NOTICE`
