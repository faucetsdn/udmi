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
* [endpoint_config_connection_success](#endpoint_config_connection_success): Push endpoint config message to device that results in success.
* [extra_config](#extra_config): Check that the device correctly handles an extra out-of-schema field
* [periodic_scan](#periodic_scan)
* [self_enumeration](#self_enumeration)
* [single_scan](#single_scan)
* [system_last_update](#system_last_update): Check that last_update state is correctly set in response to a config update.
* [system_min_loglevel](#system_min_loglevel): Check that the min log-level config is honored by the device.
* [valid_serial_no](#valid_serial_no)
* [writeback_states](#writeback_states)

## broken_config

Check that the device correctly handles a broken (non-json) config message.

1. Test failed: There were 2 errors:
  java.lang.RuntimeException(While updating config block system)
  java.lang.RuntimeException(While updating config block system)

## device_config_acked

Check that the device MQTT-acknowledges a sent config.

1. Test failed: There were 2 errors:
  java.lang.RuntimeException(While updating config block system)
  java.lang.RuntimeException(While updating config block system)

## endpoint_config_connection_error

Push endpoint config message to device that results in a connection error.

1. Update config:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "content_type": `application/json`, "base64": `eyAgICJwcm90b2NvbCI6ICJtcXR0IiwKICAiY2xpZW50X2lkIjogInByb2plY3RzLyVzL2xvY2F0aW9ucy8lcy9yZWdpc3RyaWVzLyVzL2RldmljZXMvJXMiLAogICJob3N0bmFtZSI6ICJsb2NhbGhvc3QiCn0=` } } }
1. Wait for blobset entry config status is error

## endpoint_config_connection_success

Push endpoint config message to device that results in success.

1. Test failed: There were 2 errors:
  java.lang.RuntimeException(Trying to receive message from inactive client)
  java.lang.RuntimeException(While updating config block system)

## extra_config

Check that the device correctly handles an extra out-of-schema field

1. Test failed: There were 2 errors:
  java.lang.RuntimeException(While updating config block system)
  java.lang.RuntimeException(While updating config block system)

## periodic_scan

1. Test failed: There were 2 errors:
  java.lang.RuntimeException(While updating config block system)
  java.lang.RuntimeException(While updating config block system)

## self_enumeration

1. Test failed: There were 2 errors:
  java.lang.RuntimeException(While updating config block system)
  java.lang.RuntimeException(While updating config block system)

## single_scan

1. Test failed: There were 2 errors:
  java.lang.RuntimeException(While updating config block system)
  java.lang.RuntimeException(While updating config block system)

## system_last_update

Check that last_update state is correctly set in response to a config update.

1. Test failed: There were 2 errors:
  java.lang.RuntimeException(While updating config block system)
  java.lang.RuntimeException(While updating config block system)

## system_min_loglevel

Check that the min log-level config is honored by the device.

1. Test failed: There were 2 errors:
  java.lang.RuntimeException(While updating config block system)
  java.lang.RuntimeException(While updating config block system)

## valid_serial_no

1. Test failed: There were 2 errors:
  java.lang.RuntimeException(While updating config block system)
  java.lang.RuntimeException(While updating config block system)

## writeback_states

1. Test failed: Missing 'invalid' target specification
