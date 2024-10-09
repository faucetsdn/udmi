
## system_min_loglevel (ALPHA)

Check that the min log-level config is honored by the device.

1. Wait for config sync
1. Wait for log category `system.config.apply` level `NOTICE` to be logged
1. Check that log category `system.config.apply` level `WARNING` not logged
1. Check that device config resolved within 20s
1. Wait for config sync
1. Update config warning loglevel:
    * Set `system.min_loglevel` = `400`
1. Check that log category `system.config.apply` level `NOTICE` not logged
1. Wait for config sync
1. Update config Before log category `system.config.apply` level `NOTICE` to be logged:
    * Set `system.min_loglevel` = `200`
1. Wait for log category `system.config.apply` level `NOTICE` to be logged
