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
* [endpoint_connection_bad_hash](#endpoint_connection_bad_hash): Failed connection because of bad hash.
* [endpoint_connection_success_reconnect](#endpoint_connection_success_reconnect): Check a successful reconnect to the same endpoint.
* [valid_serial_no](#valid_serial_no)
* [writeback_failure](#writeback_failure)
* [writeback_invalid](#writeback_invalid)
* [writeback_success](#writeback_success)

## endpoint_connection_bad_hash

Failed connection because of bad hash.

1. Update config before blobset status is ERROR:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `invalid blob data hash`, "url": `endpoint url` } } }
1. Wait for blobset status is ERROR
1. Check that no interesting system status

## endpoint_connection_success_reconnect

Check a successful reconnect to the same endpoint.

1. Update config before blobset phase is final and stateStatus is null:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint url` } } }
1. Wait for blobset phase is final and stateStatus is null
1. Check that no interesting system status
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

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
