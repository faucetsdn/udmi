
## system_min_loglevel (ALPHA)

Check that the min log-level config is honored by the device.

1. Wait for log category `system.config.apply` level `NOTICE` was logged
1. Check that log category `system.config.apply` level `WARNING` not logged
1. Check that device config resolved within 10s
1. Update config:
    * Set `system.min_loglevel` = `400`
1. Check that log category `system.config.apply` level `NOTICE` not logged
1. Update config before log category `system.config.apply` level `NOTICE` was logged:
    * Set `system.min_loglevel` = `200`
1. Wait for log category `system.config.apply` level `NOTICE` was logged
