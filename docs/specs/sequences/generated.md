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
* [pointset_publish_interval](#pointset_publish_interval): test sample rate and sample limit sec
* [pointset_sample_rate](#pointset_sample_rate): device publishes pointset events at a rate of no more than config sample_rate_sec
* [self_enumeration](#self_enumeration)
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
1. Check that no feature enumeration
1. Check that no point enumeration

## endpoint_connection_bad_hash

Failed connection because of bad hash.

1. Update config before blobset status is ERROR:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `invalid blob data hash`, "url": `endpoint url` } } }
1. Wait for blobset status is ERROR
1. Check that no interesting system status

## endpoint_connection_error

Push endpoint config message to device that results in a connection error.

1. Update config before blobset entry config status is error:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint url` } } }
1. Wait for blobset entry config status is error
1. Check that no interesting system status
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

## endpoint_connection_retry

Check repeated endpoint with same information gets retried.

1. Update config before blobset entry config status is error:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint url` } } }
1. Wait for blobset entry config status is error
1. Check that no interesting system status
1. Update config before blobset entry config status is error:
    * Set `blobset.blobs._iot_endpoint_config.generation` = `new generation`
1. Wait for blobset entry config status is error
1. Check that no interesting system status
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

## endpoint_connection_success_alternate

Check connection to an alternate project.

1. Wait for initial last_config matches config timestamp
1. Update config before blobset phase is apply and stateStatus is null:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint url` } } }
1. Wait for blobset phase is apply and stateStatus is null
1. Check that no interesting system status
1. Wait for blobset phase is final and stateStatus is null
1. Check that no interesting system status
1. Wait for alternate last_config matches config timestamp
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
1. Update config before blobset phase is apply and stateStatus is null:
    * Add `blobset.blobs._iot_endpoint_config` = { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint url` }
1. Wait for blobset phase is apply and stateStatus is null
1. Check that no interesting system status
1. Wait for blobset phase is final and stateStatus is null
1. Check that no interesting system status
1. Wait for restored last_config matches config timestamp
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

## endpoint_connection_success_reconnect

Check a successful reconnect to the same endpoint.

1. Update config before blobset phase is final and stateStatus is null:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint url` } } }
1. Wait for blobset phase is final and stateStatus is null
1. Check that no interesting system status
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

## extra_config

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

## family_enumeration

1. Update config before enumeration not active:
    * Add `discovery` = { "enumerate": { "families": `true` } }
1. Wait for enumeration not active
1. Update config before matching enumeration generation:
    * Add `discovery.generation` = `generation start time`
1. Wait for matching enumeration generation
1. Update config before cleared enumeration generation:
    * Remove `discovery.generation`
1. Wait for cleared enumeration generation
1. Check that family enumeration matches
1. Check that no feature enumeration
1. Check that no point enumeration

## feature_enumeration

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
1. Check that features enumerated
1. Check that no point enumeration

## multi_enumeration

1. Update config before enumeration not active:
    * Add `discovery` = { "enumerate": { "features": `true`, "uniqs": `true`, "families": `true` } }
1. Wait for enumeration not active
1. Update config before matching enumeration generation:
    * Add `discovery.generation` = `generation start time`
1. Wait for matching enumeration generation
1. Update config before cleared enumeration generation:
    * Remove `discovery.generation`
1. Wait for cleared enumeration generation
1. Check that family enumeration matches
1. Check that features enumerated
1. Check that points enumerated 3

## periodic_scan

1. Update config before all scans not active:
    * Add `discovery` = { "families": {  } }
1. Wait for all scans not active
1. Update config before scan iterations:
    * Add `discovery.families.virtual` = { "generation": `family generation`, "scan_interval_sec": `10`, "enumerate": `true` }
1. Wait for scan iterations

## pointset_enumeration

1. Update config before enumeration not active:
    * Add `discovery` = { "enumerate": { "uniqs": `true` } }
1. Wait for enumeration not active
1. Update config before matching enumeration generation:
    * Add `discovery.generation` = `generation start time`
1. Wait for matching enumeration generation
1. Update config before cleared enumeration generation:
    * Remove `discovery.generation`
1. Wait for cleared enumeration generation
1. Check that no family enumeration
1. Check that no feature enumeration
1. Check that points enumerated 3

## pointset_publish_interval

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

## pointset_sample_rate

device publishes pointset events at a rate of no more than config sample_rate_sec

1. Wait for measure initial sample rate
1. Update config before receive at least 5 pointset events:
    * Add `pointset.sample_rate_sec` = `5`
    * Add `pointset.sample_limit_sec` = `1`
1. Wait for receive at least 5 pointset events
1. Check that time period between successive pointset events is between 1 and 5 seconds

## single_scan

1. Update config before all scans not active:
    * Add `discovery` = { "families": {  } }
1. Wait for all scans not active
1. Update config before scheduled scan start:
    * Add `discovery.families.virtual` = { "generation": `family generation`, "enumerate": `true` }
1. Wait for scheduled scan start
1. Wait for scan activation
1. Wait for scan completed

## system_last_update

Check that last_update state is correctly set in response to a config update.

1. Wait for state last_config matches config timestamp

## system_min_loglevel

Check that the min log-level config is honored by the device.

1. Wait for log category `system.config.apply` level `NOTICE` was logged
1. Check that log category `system.config.apply` level `WARNING` not logged
1. Check that device config resolved within 10s
1. Update config:
    * Set `system.min_loglevel` = `400`
1. Check that log category `system.config.apply` level `NOTICE` not logged
1. Update config before log category `system.config.apply` level `NOTICE` was logged:
    * Set `system.min_loglevel` = `200`
1. Wait for log category `system.config.apply` level `NOTICE` was logged

## system_mode_restart

Restart and connect to same endpoint and expect it returns.

1. Wait for last_start is not zero
1. Check that initial count is greater than 0
1. Update config before system mode is ACTIVE:
    * Add `system.operation.mode` = `active`
1. Wait for system mode is ACTIVE
1. Update config before system mode is INITIAL:
    * Set `system.operation.mode` = `restart`
1. Wait for system mode is INITIAL
1. Check that restart count increased by one
1. Update config before system mode is ACTIVE:
    * Set `system.operation.mode` = `active`
1. Wait for system mode is ACTIVE
1. Wait for last_config is newer than previous last_config after abort
1. Wait for last_start is newer than previous last_start

## valid_serial_no

1. Wait for received serial number matches

## writeback_failure

1. Wait for point filter_alarm_pressure_status to have value_state default (null)
1. Update config before point filter_alarm_pressure_status to have value_state failure:
    * Add `pointset.points.filter_alarm_pressure_status.set_value` = `false`
1. Wait for point filter_alarm_pressure_status to have value_state failure

## writeback_invalid

1. Wait for point filter_differential_pressure_sensor to have value_state default (null)
1. Update config before point filter_differential_pressure_sensor to have value_state invalid:
    * Add `pointset.points.filter_differential_pressure_sensor.set_value` = `15`
1. Wait for point filter_differential_pressure_sensor to have value_state invalid

## writeback_success

1. Update config before point filter_differential_pressure_setpoint to have value_state default (null):
    * Remove `pointset.points.filter_differential_pressure_setpoint.set_value`
1. Wait for point filter_differential_pressure_setpoint to have value_state default (null)
1. Update config before point filter_differential_pressure_setpoint to have value_state applied:
    * Add `pointset.points.filter_differential_pressure_setpoint.set_value` = `60`
1. Wait for point filter_differential_pressure_setpoint to have value_state applied
1. Wait for point `filter_differential_pressure_setpoint` to have present_value `60`
