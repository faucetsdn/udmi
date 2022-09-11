
## extra_config

Check that the device correctly handles an extra out-of-schema field

1. Update config:
    * Set `system.min_loglevel` = `100`
1. Wait for last_config not null
1. Wait for system operational
1. Wait for no interesting status
1. Wait for log category `system.config.receive` level `DEBUG`
1. Wait for last_config updated
1. Wait for system operational
1. Wait for no interesting status
1. Wait for log category `system.config.parse` level `DEBUG`
1. Wait for log category `system.config.apply` level `NOTICE`
1. Wait for log category `system.config.receive` level `DEBUG`
1. Wait for last_config updated again
1. Wait for system operational
1. Wait for no interesting status
1. Wait for log category `system.config.parse` level `DEBUG`
1. Wait for log category `system.config.apply` level `NOTICE`
