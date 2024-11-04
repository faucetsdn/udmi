
## broken_config (STABLE)

Check that the device correctly handles a broken (non-json) config message.

1. Update config to enable debug logging
    * Set `system.min_loglevel` = `100`
1. Wait until system logs level `NOTICE` category `system.config.apply`
1. Update config to force broken (invalid JSON) configuration
1. Wait until has system status level is >= WARNING (400)
1. Check that status level is exactly `ERROR` (500)
1. Check that category matches `system.config.parse`
1. Check that device state `last_config` has not been updated
1. Wait until system logs level `DEBUG` category `system.config.receive`
1. Wait until system logs level `ERROR` category `system.config.parse`
1. Check that log level `NOTICE` (or greater) category `system.config.apply` was not logged
1. Reset config to clean version
1. (Log level is implicitly set to `INFO` through config reset)
1. Wait until system logs level `NOTICE` category `system.config.apply`
1. Check that log level `DEBUG` (or greater) category `system.config.receive` was not logged
1. Check that log level `DEBUG` (or greater) category `system.config.parse` was not logged
