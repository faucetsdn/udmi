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
* [endpoint_config_connection_success_redirect](#endpoint_config_connection_success_redirect): Redirect to a different endpoint
* [endpoint_config_connection_success_restart](#endpoint_config_connection_success_restart): Restart and connect to same endpoint and expect it returns.
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
    * Set `version` = `1.3.14-73-g0d775b9d-dirty`
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
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "content_type": `application/json`, "base64": `eyAgICJwcm90b2NvbCI6ICJtcXR0IiwKICAiY2xpZW50X2lkIjogInByb2plY3RzL2Jvcy1wZXJpbmdrbmlmZS1kZXYvbG9jYXRpb25zL3VzLWNlbnRyYWwxL3JlZ2lzdHJpZXMvWlotVFJJLUZFQ1RBL2RldmljZXMvQUhVLTEiLAogICJob3N0bmFtZSI6ICJsb2NhbGhvc3QiCn0=` } } }
1. Wait for blobset entry config status is error

## endpoint_config_connection_success_reconnect

Push endpoint config message to device that results in successful reconnect to the same endpoint.

1. Update config before blobset entry config status is success:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "content_type": `application/json`, "base64": `eyAgICJwcm90b2NvbCI6ICJtcXR0IiwKICAiY2xpZW50X2lkIjogInByb2plY3RzL2Jvcy1wZXJpbmdrbmlmZS1kZXYvbG9jYXRpb25zL3VzLWNlbnRyYWwxL3JlZ2lzdHJpZXMvWlotVFJJLUZFQ1RBL2RldmljZXMvQUhVLTEiLAogICJob3N0bmFtZSI6ICJtcXR0Lmdvb2dsZWFwaXMuY29tIgp9`, "nonce": `hq8aSyrM39aZwcCRK8T0KbnXB36pm2Fac9T58vXn4YY=` } } }
1. Wait for blobset entry config status is success

## endpoint_config_connection_success_redirect

Redirect to a different endpoint

1. Update config:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "content_type": `application/json`, "base64": `eyAgICJwcm90b2NvbCI6ICJtcXR0IiwKICAiY2xpZW50X2lkIjogInByb2plY3RzL2Jvcy1wZXJpbmdrbmlmZS1kZXYvbG9jYXRpb25zL3VzLWNlbnRyYWwxL3JlZ2lzdHJpZXMvWlotVFJJLUZFQ1RBL2RldmljZXMvQUhVLTk5IiwKICAiaG9zdG5hbWUiOiAibXF0dC5nb29nbGVhcGlzLmNvbSIKfQ==`, "nonce": `w/FQDn8mtR2WEiWDKoCtUpIioNQMkL2bffaUg/vZPHk=` } } }
1. Wait for blobset entry config status is success

## endpoint_config_connection_success_restart

Restart and connect to same endpoint and expect it returns.

1. Wait for last_start is not zero
1. Update config:
    * Add `system.mode` = `active`
1. Wait for deviceState.system.mode == ACTIVE
1. Update config:
    * Set `system.mode` = `restart`
1. Wait for deviceState.system.mode == INITIAL
1. Update config:
    * Set `system.mode` = `active`
1. Wait for deviceState.system.mode == ACTIVE
1. Wait for last_config is newer than previous last_config
1. Wait for last_start is newer than previous last_start 2022-11-11T19:25:49Z
