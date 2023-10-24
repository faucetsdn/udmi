[**UDMI**](../../../) / [**Docs**](../../) / [**Specs**](../) / [**Sequences**](./) / [Generated](#)

# Generated sequences

These are the exact sequences being checked by the sequence tool. They are programmatically generated
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
* [config_logging](#config_logging-beta): Check that the device publishes minimum required log entries when receiving config
* [device_config_acked](#device_config_acked-beta): Check that the device MQTT-acknowledges a sent config.
* [empty_enumeration](#empty_enumeration-preview): check enumeration of nothing at all
* [extra_config](#extra_config-beta): Check that the device correctly handles an extra out-of-schema field
* [feature_enumeration](#feature_enumeration-preview): check enumeration of device features
* [pointset_publish_interval](#pointset_publish_interval-beta): test sample rate and sample limit sec
* [pointset_sample_rate](#pointset_sample_rate-beta): device publishes pointset events at a rate of no more than config sample_rate_sec
* [system_last_update](#system_last_update-stable): Check that last_update state is correctly set in response to a config update.

## config_logging (BETA)

Check that the device publishes minimum required log entries when receiving config

1. Test failed: timeout waiting for config sync

## device_config_acked (BETA)

Check that the device MQTT-acknowledges a sent config.

1. Wait for config acked

## empty_enumeration (PREVIEW)

check enumeration of nothing at all

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
1. Check that no feature enumeration
1. Check that no point enumeration

## extra_config (BETA)

Check that the device correctly handles an extra out-of-schema field

1. Update config before last_config not null:
    * Set `system.min_loglevel` = `100`
1. Wait for last_config not null
1. Wait for system operational
1. Check that no interesting system status
1. Wait for log category `system.config.receive` level `DEBUG` was logged
1. Wait for last_config updated
1. Wait for system operational
1. Check that no interesting system status
1. Wait for log category `system.config.parse` level `DEBUG` was logged
1. Wait for log category `system.config.apply` level `NOTICE` was logged
1. Wait for log category `system.config.receive` level `DEBUG` was logged
1. Wait for last_config updated again
1. Wait for system operational
1. Check that no interesting system status
1. Wait for log category `system.config.parse` level `DEBUG` was logged
1. Wait for log category `system.config.apply` level `NOTICE` was logged

## feature_enumeration (PREVIEW)

check enumeration of device features

1. Update config before enumeration not active:
    * Add `discovery` = { "enumerate": { "features": `true` } }
1. Wait for enumeration not active
1. Update config before matching enumeration generation:
    * Add `discovery.generation` = `generation start time`
1. Wait for matching enumeration generation
1. Update config before cleared enumeration generation:
    * Remove `discovery.generation`
1. Wait for cleared enumeration generation
1. Check that no family enumeration
1. Check that feature enumeration matches metadata
1. Check that all enumerated features are official buckets
1. Check that no point enumeration

## pointset_publish_interval (BETA)

test sample rate and sample limit sec

1. Update config before receive at least 4 pointset events:
    * Add `pointset.sample_rate_sec` = `8`
    * Add `pointset.sample_limit_sec` = `5`
1. Wait for receive at least 4 pointset events
1. Check that time period between successive pointset events is between 5 and 8 seconds
1. Update config before receive at least 4 pointset events:
    * Set `pointset.sample_rate_sec` = `18`
    * Set `pointset.sample_limit_sec` = `15`
1. Wait for receive at least 4 pointset events
1. Check that time period between successive pointset events is between 15 and 18 seconds

## pointset_sample_rate (BETA)

device publishes pointset events at a rate of no more than config sample_rate_sec

1. Wait for measure initial sample rate
1. Update config before receive at least 5 pointset events:
    * Add `pointset.sample_rate_sec` = `5`
    * Add `pointset.sample_limit_sec` = `1`
1. Wait for receive at least 5 pointset events
1. Check that time period between successive pointset events is between 1 and 5 seconds

## system_last_update (STABLE)

Check that last_update state is correctly set in response to a config update.

1. Wait for state last_config matches config timestamp
