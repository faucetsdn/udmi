
## extra_config

Check that the device correctly handles an extra out-of-schema field

1. Update config before last_config not null:
    * Set `system.min_loglevel` = `100`
1. Wait for last_config not null
1. Wait for system operational
1. Check that no interesting status
1. Wait for log category `system.config.receive` level `DEBUG` was logged
1. Wait for last_config updated
1. Wait for system operational
1. Check that no interesting status
1. Wait for log category `system.config.parse` level `DEBUG` was logged
1. Wait for log category `system.config.apply` level `NOTICE` was logged
1. Wait for log category `system.config.receive` level `DEBUG` was logged
1. Wait for last_config updated again
1. Wait for system operational
1. Check that no interesting status
1. Wait for log category `system.config.parse` level `DEBUG` was logged
1. Wait for log category `system.config.apply` level `NOTICE` was logged
