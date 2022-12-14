
## broken_config

Check that the device correctly handles a broken (non-json) config message.

1. Update config before no interesting status:
    * Set `system.min_loglevel` = `100`
1. Wait for no interesting status
1. Wait for state synchronized
1. Test failed: timeout waiting for state synchronized
