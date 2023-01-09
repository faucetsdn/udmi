
## system_min_loglevel

Check that the min log-level config is honored by the device.

1. Check has not logged category `system.config.apply` level `NOTICE` (**incomplete!**)
1. Update config:
    * Set `system.min_loglevel` = `400`
1. Update config before log category `system.config.apply` level `NOTICE`:
    * Set `system.min_loglevel` = `200`
1. Wait for log category `system.config.apply` level `NOTICE`
