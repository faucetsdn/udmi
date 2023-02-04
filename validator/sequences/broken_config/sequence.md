
## broken_config (ALPHA)

Check that the device correctly handles a broken (non-json) config message.

1. Update config:
    * Set `system.min_loglevel` = `100`
1. Wait for state synchronized
1. Check that initial stable_config matches last_config
1. Wait for log category `system.config.apply` level `NOTICE` was logged
1. Wait for log category `system.config.receive` level `DEBUG` was logged
1. Check that interesting system status
1. Wait for log category `system.config.parse` level `ERROR` was logged
1. Check that log category `system.config.apply` level `NOTICE` not logged
1. Force reset config
1. Check that no interesting system status
1. Update config before last_config updated:
    * Set `system.min_loglevel` = `100`
1. Wait for last_config updated
1. Wait for log category `system.config.apply` level `NOTICE` was logged
1. Check that log category `system.config.receive` level `DEBUG` not logged
1. Check that log category `system.config.parse` level `DEBUG` not logged
