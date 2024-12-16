
## extra_config (STABLE)

Check that the device correctly handles an extra out-of-schema field

1. Wait for last_config not null
1. Wait for system operational
1. Check that system status level is not >= `WARNING` (400)
1. Update config Before system logs level `DEBUG` category `system.config.receive`
1. Wait until system logs level `DEBUG` category `system.config.receive`
1. Failed waiting until last_config updated

Test failed: Failed waiting until last_config updated
