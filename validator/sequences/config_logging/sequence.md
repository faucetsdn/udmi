
## config_logging (STABLE)

Check that the device publishes minimum required log entries when receiving config

1. Wait for config sync
1. Update config set min_loglevel to debug:
    * Set `system.min_loglevel` = `100`
1. Wait for config sync
1. Force config update to resend config to device
1. Wait for log category `system.config.receive` level `DEBUG` to be logged
1. Wait for log category `system.config.parse` level `DEBUG` to be logged
1. Wait for log category `system.config.apply` level `NOTICE` to be logged
