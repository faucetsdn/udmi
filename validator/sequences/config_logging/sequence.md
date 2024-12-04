
## config_logging (STABLE)

Check that the device publishes minimum required log entries when receiving config

1. Force config update to resend config to device
1. Wait until system logs level `DEBUG` category `system.config.receive`
1. Wait until system logs level `DEBUG` category `system.config.parse`
1. Wait until system logs level `NOTICE` category `system.config.apply`

Test passed.
