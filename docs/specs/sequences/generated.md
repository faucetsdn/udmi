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
* [config_logging](#config_logging): Check that the device publishes minimum required log entries when receiving config
* [device_config_acked](#device_config_acked): Check that the device MQTT-acknowledges a sent config.
* [empty_enumeration](#empty_enumeration): check enumeration of nothing at all
* [extra_config](#extra_config): Check that the device correctly handles an extra out-of-schema field
* [feature_enumeration](#feature_enumeration): check enumeration of device features
* [pointset_publish](#pointset_publish): device publishes pointset events
* [pointset_publish_interval](#pointset_publish_interval): test sample rate and sample limit sec
* [pointset_remove_point](#pointset_remove_point): pointset state does not report unconfigured point
* [pointset_request_extraneous](#pointset_request_extraneous): pointset configuration contains extraneous point
* [pointset_sample_rate](#pointset_sample_rate): device publishes pointset events at a rate of no more than config sample_rate_sec
* [state_make_model](#state_make_model): device publishes correct make and model information in state messages
* [state_software](#state_software): device publishes correct software information in state messages
* [system_last_update](#system_last_update): Check that last_update state is correctly set in response to a config update.

## config_logging (BETA)

Check that the device publishes minimum required log entries when receiving config

1. Update config set min_loglevel to debug:
    * Set `system.min_loglevel` = `100`
1. Force config update to resend config to device
1. Wait for log category `system.config.receive` level `DEBUG` was logged
1. Wait for log category `system.config.parse` level `DEBUG` was logged
1. Wait for log category `system.config.apply` level `NOTICE` was logged

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

## pointset_publish (BETA)

device publishes pointset events

1. Wait for receive a pointset event

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

## pointset_remove_point (BETA)

pointset state does not report unconfigured point

1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value
1. Update config before pointset status does not contain removed point:
    * Remove `pointset.points.filter_differential_pressure_setpoint`
1. Wait for pointset status does not contain removed point
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value
1. Update config before pointset status contains removed point:
    * Add `pointset.points.filter_differential_pressure_setpoint` = { "set_value": `98`, "units": `Bars` }
1. Wait for pointset status contains removed point
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value

## pointset_request_extraneous (BETA)

pointset configuration contains extraneous point

1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value
1. Update config before pointset status contains extraneous point error:
    * Add `pointset.points.extraneous_point` = {  }
1. Wait for pointset status contains extraneous point error
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value
1. Update config before pointset status removes extraneous point error:
    * Remove `pointset.points.extraneous_point`
1. Wait for pointset status removes extraneous point error
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value

## pointset_sample_rate (BETA)

device publishes pointset events at a rate of no more than config sample_rate_sec

1. Wait for measure initial sample rate
1. Update config before receive at least 5 pointset events:
    * Add `pointset.sample_rate_sec` = `5`
    * Add `pointset.sample_limit_sec` = `1`
1. Wait for receive at least 5 pointset events
1. Check that time period between successive pointset events is between 1 and 5 seconds

## state_make_model (BETA)

device publishes correct make and model information in state messages

1. Check that make and model in state matches make in metadata

## state_software (BETA)

device publishes correct software information in state messages

1. Check that software in metadata matches state

## system_last_update (STABLE)

Check that last_update state is correctly set in response to a config update.

1. Wait for state last_config matches config timestamp
