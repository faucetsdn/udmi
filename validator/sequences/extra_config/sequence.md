
## extra_config (STABLE)

Check that the device correctly handles an extra out-of-schema field

1. Update config before last_config not null
    * Set `system.min_loglevel` = `100`
1. Wait for last_config not null
1. Wait for system operational
1. Check that system status level is not >= `WARNING` (400)
1. Update config Before system logs level `DEBUG` category `system.config.receive`
1. Wait until system logs level `DEBUG` category `system.config.receive`
1. Wait for last_config updated
1. Wait for system operational
1. Check that system status level is not >= `WARNING` (400)
1. Wait until system logs level `DEBUG` category `system.config.parse`
1. Wait until system logs level `NOTICE` category `system.config.apply`
1. Update config Before system logs level `DEBUG` category `system.config.receive`
1. Wait until system logs level `DEBUG` category `system.config.receive`
1. Wait for last_config updated again
1. Wait for system operational
1. Wait until system logs level `DEBUG` category `system.config.parse`
1. Wait until system logs level `NOTICE` category `system.config.apply`

Test passed.
