
## system_min_loglevel (ALPHA)

Check that the min log-level config is honored by the device.

1. Update config Before system logs level `NOTICE` category `system.config.apply`
    * Set `system.min_loglevel` = `200`
1. Wait until system logs level `NOTICE` category `system.config.apply`
1. Check that device config resolved within 20s
1. Update config warning loglevel
    * Set `system.min_loglevel` = `400`
1. Check that log level `NOTICE` (or greater) category `system.config.apply` was not logged
1. Update config Before system logs level `NOTICE` category `system.config.apply`
    * Set `system.min_loglevel` = `100`
1. Wait until system logs level `NOTICE` category `system.config.apply`

Test passed.
