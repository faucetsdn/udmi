[**UDMI**](../../../) / [**Docs**](../../) / [**Specs**](../) / [**Sequences**](./) / [Generated](#)

# Generated sequences

These are the exact sequences being checked by the sequence tool. They are programaticaly generated
so maybe a bit cryptic, but they accurately represent the specific steps required for each test.

<!--

To regenerate the contents of this file below, first generate a message trace sequence and then run bin/gencode_seq

* Running "bin/test_sequencer target-gcp-project" will run through the complete battery of test sequences
  against the AHU-1 device to create the requisite trace files. This takes about 4 min for a complete test run.

* Then run "bin/gencode_seq" which consumes the generated trace files and creates "generated.md" (this file)
  with the output. The diff (using git, usually) should then reflect the changes against the committed version.

Some caveats:

* Flaky tests are annoying. Sometimes something goes wrong and one entire test will be borked. Easist thing
  is to just re-run the sequence tests until it's clean, but that's not always the fastest.

* The gencode part requires a complete test run to work properly, but you can run individual test runs
  as needed, e.g. "bin/sequencer sites/udmi_site/model target-gcp-project AHU-1 21632 system_last_update"
  (you will need to run an instance of pubber separately).

-->

<!-- START GENERATED, do not edit anything after this line! -->
* [broken_config](#broken_config): Check that the device correctly handles a broken (non-json) config message.
* [device_config_acked](#device_config_acked): Check that the device MQTT-acknowledges a sent config.
* [empty_enumeration](#empty_enumeration)
* [endpoint_connection_bad_hash](#endpoint_connection_bad_hash): Failed connection because of bad hash.
* [endpoint_connection_error](#endpoint_connection_error): Push endpoint config message to device that results in a connection error.
* [endpoint_connection_retry](#endpoint_connection_retry): Check repeated endpoint with same information gets retried.
* [endpoint_connection_success_alternate](#endpoint_connection_success_alternate): Check connection to an alternate project.
* [endpoint_connection_success_reconnect](#endpoint_connection_success_reconnect): Check a successful reconnect to the same endpoint.
* [extra_config](#extra_config): Check that the device correctly handles an extra out-of-schema field
* [family_enumeration](#family_enumeration)
* [feature_enumeration](#feature_enumeration)
* [multi_enumeration](#multi_enumeration)
* [periodic_scan](#periodic_scan)
* [pointset_enumeration](#pointset_enumeration)
* [single_scan](#single_scan)
* [system_last_update](#system_last_update): Check that last_update state is correctly set in response to a config update.
* [system_min_loglevel](#system_min_loglevel): Check that the min log-level config is honored by the device.
* [system_mode_restart](#system_mode_restart): Restart and connect to same endpoint and expect it returns.
* [valid_serial_no](#valid_serial_no)
* [writeback_failure](#writeback_failure)
* [writeback_invalid](#writeback_invalid)
* [writeback_success](#writeback_success)

## broken_config

Check that the device correctly handles a broken (non-json) config message.

1. Update config:
    * Set `system.min_loglevel` = `100`
1. Wait for state synchronized
1. Check that initial stable_config matches last_config
1. Wait for log category `system.config.apply` level `NOTICE` was logged
1. Wait for log category `system.config.receive` level `DEBUG` was logged
1. Check that interesting system status
1. Wait for log category `system.config.parse` level `ERROR` was logged
1. Check that log category `system.config.apply` level `NOTICE` not logged
1. Force reset config
1. Check that no interesting system status
1. Update config before last_config updated:
    * Add `system.last_start` = `device reported`
    * Set `system.min_loglevel` = `100`
1. Wait for last_config updated
1. Wait for log category `system.config.apply` level `NOTICE` was logged
1. Check that log category `system.config.receive` level `DEBUG` not logged
1. Check that log category `system.config.parse` level `DEBUG` not logged

## device_config_acked

Check that the device MQTT-acknowledges a sent config.

1. Wait for config acked

## empty_enumeration

1. Update config before enumeration not active:
    * Add `discovery` = { "enumerate": {  } }
1. Wait for enumeration not active
1. Update config before matching enumeration generation:
    * Add `discovery.generation` = `generation start time`
1. Wait for matching enumeration generation
1. Update config before cleared enumeration generation:
    * Remove `discovery.generation`
1. Wait for cleared enumeration generation
1. Check that no family enumeration
1. Check that no point enumeration
1. Check that no feature enumeration
