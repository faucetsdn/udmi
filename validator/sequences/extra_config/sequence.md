
## extra_config (STABLE)

Check that the device correctly handles an extra out-of-schema field

1. Wait for config sync
1. Update config before last_config not null:
    * Set `system.min_loglevel` = `100`
1. Wait for last_config not null
1. Wait for system operational
1. Check that no significant system status exists
1. Wait for config sync
1. Wait for log category `system.config.receive` level `DEBUG` to be logged
1. Wait for last_config updated
1. Wait for system operational
1. Check that no significant system status exists
1. Wait for log category `system.config.parse` level `DEBUG` to be logged
1. Wait for log category `system.config.apply` level `NOTICE` to be logged
1. Wait for config sync
1. Wait for log category `system.config.receive` level `DEBUG` to be logged
1. Wait for last_config updated again
1. Wait for system operational
1. Wait for log category `system.config.parse` level `DEBUG` to be logged
1. Wait for log category `system.config.apply` level `NOTICE` to be logged
