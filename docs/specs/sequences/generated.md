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
* [endpoint_config_connection_error](#endpoint_config_connection_error): Push endpoint config message to device that results in a connection error.
* [endpoint_config_connection_success_reconnect](#endpoint_config_connection_success_reconnect): Push endpoint config message to device that results in successful reconnect to the same endpoint.
* [extra_config](#extra_config): Check that the device correctly handles an extra out-of-schema field
* [periodic_scan](#periodic_scan)
* [self_enumeration](#self_enumeration)
* [single_scan](#single_scan)
* [system_last_update](#system_last_update): Check that last_update state is correctly set in response to a config update.
* [system_min_loglevel](#system_min_loglevel): Check that the min log-level config is honored by the device.
* [valid_serial_no](#valid_serial_no)
* [writeback_failure_state](#writeback_failure_state)
* [writeback_invalid_state](#writeback_invalid_state)
* [writeback_success_apply](#writeback_success_apply)
* [writeback_success_state](#writeback_success_state)

## broken_config

Check that the device correctly handles a broken (non-json) config message.

1. Update config before no interesting status:
    * Set `system.min_loglevel` = `100`
1. Wait for no interesting status
1. Wait for state synchronized
1. Check that initial stable_config matches last_config
1. Wait for log category `system.config.receive` level `DEBUG`
1. Wait for has interesting status
1. Wait for log category `system.config.parse` level `ERROR`
1. Check has not logged category `system.config.apply` level `NOTICE` (**incomplete!**)
1. Force reset config
1. Update config before log category `system.config.receive` level `DEBUG`:
    * Add `system.last_start` = `device reported`
1. Wait for log category `system.config.receive` level `DEBUG`
1. Wait for no interesting status
1. Wait for last_config updated
1. Wait for log category `system.config.apply` level `NOTICE`
1. Wait for log category `system.config.parse` level `DEBUG`

## device_config_acked

Check that the device MQTT-acknowledges a sent config.

1. Wait for config acked

## endpoint_config_connection_error

Push endpoint config message to device that results in a connection error.

1. Update config before blobset entry config status is error:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "content_type": `application/json`, "base64": `endpoint_base64_payload` } } }
1. Wait for blobset entry config status is error

## endpoint_config_connection_success_reconnect

Push endpoint config message to device that results in successful reconnect to the same endpoint.

1. Update config before blobset entry config status is success:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "content_type": `application/json`, "base64": `endpoint_base64_payload`, "nonce": `endpoint_nonce` } } }
1. Wait for blobset entry config status is success

Check that the device correctly handles an extra out-of-schema field

1. Update config before last_config not null:
    * Set `system.min_loglevel` = `100`
1. Wait for last_config not null
1. Wait for system operational
1. Wait for no interesting status
1. Wait for log category `system.config.receive` level `DEBUG`
1. Wait for last_config updated
1. Wait for system operational
1. Wait for no interesting status
1. Wait for log category `system.config.parse` level `DEBUG`
1. Wait for log category `system.config.apply` level `NOTICE`
1. Wait for log category `system.config.receive` level `DEBUG`
1. Wait for last_config updated again
1. Wait for system operational
1. Wait for no interesting status
1. Wait for log category `system.config.parse` level `DEBUG`
1. Wait for log category `system.config.apply` level `NOTICE`

## periodic_scan

1. Update config before all scans not active:
    * Add `discovery` = { "families": {  } }
1. Wait for all scans not active
1. Update config before scan iterations:
    * Add `discovery.families.virtual` = { "generation": `family generation`, "scan_interval_sec": `10`, "enumerate": `true` }
1. Wait for scan iterations

## self_enumeration

1. Wait for enumeration not active
1. Update config before enumeration generation:
    * Add `discovery` = { "enumeration": { "generation": `generation start time` } }
1. Wait for enumeration generation
1. Wait for enumeration still not active

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

1. Check has not logged category `system.config.apply` level `NOTICE` (**incomplete!**)
1. Update config:
    * Set `system.min_loglevel` = `400`
1. Update config before log category `system.config.apply` level `NOTICE`:
    * Set `system.min_loglevel` = `200`
1. Wait for log category `system.config.apply` level `NOTICE`

## valid_serial_no

1. Wait for received serial no matches

## writeback_failure_state

1. Wait for point filter_alarm_pressure_status to have value_state default (null)
1. Update config before point filter_alarm_pressure_status to have value_state failure:
    * Add `pointset.points.filter_alarm_pressure_status.set_value` = `false`
1. Wait for point filter_alarm_pressure_status to have value_state failure

## writeback_invalid_state

1. Wait for point filter_differential_pressure_sensor to have value_state default (null)
1. Update config before point filter_differential_pressure_sensor to have value_state invalid:
    * Add `pointset.points.filter_differential_pressure_sensor.set_value` = `15`
1. Wait for point filter_differential_pressure_sensor to have value_state invalid

## writeback_success_apply

1. Update config before point `filter_differential_pressure_setpoint` to have present_value `60`:
    * Set `pointset.points.filter_differential_pressure_setpoint.set_value` = `60`
1. Wait for point `filter_differential_pressure_setpoint` to have present_value `60`

## writeback_success_state

1. Wait for point filter_differential_pressure_setpoint to have value_state default (null)
1. Update config before point filter_differential_pressure_setpoint to have value_state applied:
    * Set `pointset.points.filter_differential_pressure_setpoint.set_value` = `60`
1. Wait for point filter_differential_pressure_setpoint to have value_state applied
1. Wait for point `filter_differential_pressure_setpoint` to have present_value `60`
