
## config_logging (BETA)

Check that the device publishes minimum required log entries when receiving config

1. Update config set min_loglevel to debug:
    * Set `system.min_loglevel` = `100`
1. Force config update to resend config to device
1. Wait for log category `system.config.receive` level `DEBUG` was logged
1. Wait for log category `system.config.parse` level `DEBUG` was logged
1. Wait for log category `system.config.apply` level `NOTICE` was logged
