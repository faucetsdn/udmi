
## broken_config (STABLE)

Check that the device correctly handles a broken (non-json) config message.

1. Update config to enable debug logging
    * Set `system.min_loglevel` = `100`
1. _logging_ Wait until system logs level `NOTICE` category `system.config.apply`
1. Update config to force broken (invalid JSON) configuration
1. _status_ Wait until system status level is >= `WARNING` (400)
1. _status_ Check that status level is exactly `ERROR` (500)
1. _status_ Check that category matches `system.config.parse`
1. Check that device state `last_config` has not been updated
1. _logging_ Wait until system logs level `DEBUG` category `system.config.receive`
1. _logging_ Wait until system logs level `ERROR` category `system.config.parse`
1. Reset config to clean version
1. (Log level is implicitly set to `INFO` through config reset)
1. _status_ Wait until system status level is not >= `WARNING` (400)
1. Check that device state `last_config` has been updated

Test passed.
