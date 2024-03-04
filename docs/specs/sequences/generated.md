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

* Test resuts in sites/udmi_site_model` take`` precedence
  over files in the sequencer cache. If using imported 
  github artifacts, remove the `sites/udmi_site_model`
  directory to generate the document
-->

<!-- START GENERATED, do not edit anything after this line! -->
* [broken_config](#broken_config-beta): Check that the device correctly handles a broken (non-json) config message.
* [config_logging](#config_logging-beta): Check that the device publishes minimum required log entries when receiving config
* [device_config_acked](#device_config_acked-beta): Check that the device MQTT-acknowledges a sent config.
* [empty_enumeration](#empty_enumeration-preview): Check enumeration of nothing at all
* [endpoint_connection_error](#endpoint_connection_error-preview): Push endpoint config message to device that results in a connection error.
* [endpoint_connection_retry](#endpoint_connection_retry-preview): Check repeated endpoint with same information gets retried.
* [endpoint_connection_success_alternate](#endpoint_connection_success_alternate-preview): Check connection to an alternate project.
* [endpoint_connection_success_reconnect](#endpoint_connection_success_reconnect-preview): Check a successful reconnect to the same endpoint.
* [endpoint_failure_and_restart](#endpoint_failure_and_restart-preview)
* [endpoint_redirect_and_restart](#endpoint_redirect_and_restart-preview)
* [extra_config](#extra_config-beta): Check that the device correctly handles an extra out-of-schema field
* [family_ether_addr](#family_ether_addr-preview)
* [family_ipv4_addr](#family_ipv4_addr-preview)
* [family_ipv6_addr](#family_ipv6_addr-preview)
* [feature_enumeration](#feature_enumeration-preview): Check enumeration of device features
* [gateway_attach_handling](#gateway_attach_handling-preview): Check adequate logging for gateway detach, errors, and reattach
* [gateway_proxy_events](#gateway_proxy_events-beta): Check that a gateway proxies pointset events for indicated devices
* [pointset_publish](#pointset_publish-beta): Check that a device publishes pointset events
* [pointset_publish_interval](#pointset_publish_interval-beta): Check handling of sample_rate_sec and sample_limit_sec
* [pointset_remove_point](#pointset_remove_point-beta): Check that pointset state does not report an unconfigured point
* [pointset_request_extraneous](#pointset_request_extraneous-beta): Check error when pointset configuration contains extraneous point
* [state_make_model](#state_make_model-beta): Check that a device publishes correct make and model information in state messages
* [state_software](#state_software-beta): Check that a device publishes correct software information in state messages
* [system_last_update](#system_last_update-stable): Check that last_update state is correctly set in response to a config update.
* [valid_serial_no](#valid_serial_no-beta)

## broken_config (BETA)

Check that the device correctly handles a broken (non-json) config message.

1. Update config starting broken_config:
    * Set `system.min_loglevel` = `100`
1. Wait for initial state synchronized
1. Check that initial stable_config matches last_config
1. Wait for log category `system.config.apply` level `NOTICE` to be logged
1. Wait for has applicable system status
1. Check that applicable system status
1. Wait for log category `system.config.receive` level `DEBUG` to be logged
1. Wait for log category `system.config.parse` level `ERROR` to be logged
1. Check that log category `system.config.apply` level `NOTICE` not logged
1. Force reset config
1. Wait for state last_config sync
1. Wait for log category `system.config.apply` level `NOTICE` to be logged
1. Wait for restored state synchronized
1. Update config before last_config updated:
    * Set `system.min_loglevel` = `100`
1. Wait for last_config updated
1. Wait for log category `system.config.apply` level `NOTICE` to be logged
1. Check that log category `system.config.receive` level `DEBUG` not logged
1. Check that log category `system.config.parse` level `DEBUG` not logged

## config_logging (BETA)

Check that the device publishes minimum required log entries when receiving config

1. Update config set min_loglevel to debug:
    * Set `system.min_loglevel` = `100`
1. Force config update to resend config to device
1. Wait for log category `system.config.receive` level `DEBUG` to be logged
1. Wait for log category `system.config.parse` level `DEBUG` to be logged
1. Wait for log category `system.config.apply` level `NOTICE` to be logged

## device_config_acked (BETA)

Check that the device MQTT-acknowledges a sent config.

1. Wait for config acked

## empty_enumeration (PREVIEW)

Check enumeration of nothing at all

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

## endpoint_connection_error (PREVIEW)

Push endpoint config message to device that results in a connection error.

1. Update config before blobset entry config status is error:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset entry config status is error
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

## endpoint_connection_retry (PREVIEW)

Check repeated endpoint with same information gets retried.

1. Update config before blobset entry config status is error:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset entry config status is error
1. Update config before blobset entry config status is error:
    * Set `blobset.blobs._iot_endpoint_config.generation` = `new generation`
1. Wait for blobset entry config status is error
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

## endpoint_connection_success_alternate (PREVIEW)

Check connection to an alternate project.

1. Wait for initial last_config matches config timestamp
1. Update config mirroring config false:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset phase is apply and stateStatus is null
1. Wait for blobset phase is final and stateStatus is null
1. Wait for alternate last_config matches config timestamp
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
1. Update config mirroring config true:
    * Add `blobset.blobs._iot_endpoint_config` = { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` }
1. Wait for blobset phase is apply and stateStatus is null
1. Wait for blobset phase is final and stateStatus is null
1. Wait for restored last_config matches config timestamp
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

## endpoint_connection_success_reconnect (PREVIEW)

Check a successful reconnect to the same endpoint.

1. Update config before blobset phase is final and stateStatus is null:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset phase is final and stateStatus is null
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

## endpoint_failure_and_restart (PREVIEW)

1. Update config before blobset entry config status is error:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset entry config status is error
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
1. Wait for last_config is newer than previous last_config before abort
1. Wait for last_config is newer than previous last_config after abort
1. Wait for last_start is newer than previous last_start
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

## endpoint_redirect_and_restart (PREVIEW)

1. Wait for initial last_config matches config timestamp
1. Update config mirroring config false:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset phase is apply and stateStatus is null
1. Wait for blobset phase is final and stateStatus is null
1. Wait for alternate last_config matches config timestamp
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
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
1. Wait for last_config is newer than previous last_config before abort
1. Wait for last_config is newer than previous last_config after abort
1. Wait for last_start is newer than previous last_start
1. Update config mirroring config true:
    * Add `blobset.blobs._iot_endpoint_config` = { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` }
1. Wait for blobset phase is apply and stateStatus is null
1. Wait for blobset phase is final and stateStatus is null
1. Wait for restored last_config matches config timestamp
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

## extra_config (BETA)

Check that the device correctly handles an extra out-of-schema field

1. Update config before last_config not null:
    * Set `system.min_loglevel` = `100`
1. Wait for last_config not null
1. Wait for system operational
1. Check that no applicable system status
1. Wait for log category `system.config.receive` level `DEBUG` to be logged
1. Wait for last_config updated
1. Wait for system operational
1. Check that no applicable system status
1. Wait for log category `system.config.parse` level `DEBUG` to be logged
1. Wait for log category `system.config.apply` level `NOTICE` to be logged
1. Wait for log category `system.config.receive` level `DEBUG` to be logged
1. Wait for last_config updated again
1. Wait for system operational
1. Wait for log category `system.config.parse` level `DEBUG` to be logged
1. Wait for log category `system.config.apply` level `NOTICE` to be logged

## family_ether_addr (PREVIEW)

1. Wait for localnet families available
1. Check that device family ether address matches

## family_ipv4_addr (PREVIEW)

1. Wait for localnet families available
1. Check that device family ipv4 address matches

## family_ipv6_addr (PREVIEW)

1. Wait for localnet families available
1. Check that device family ipv6 address matches

## feature_enumeration (PREVIEW)

Check enumeration of device features

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

## gateway_attach_handling (PREVIEW)

Check adequate logging for gateway detach, errors, and reattach

1. Test skipped: Not a gateway

## gateway_proxy_events (BETA)

Check that a gateway proxies pointset events for indicated devices

1. Test skipped: Not a gateway

## pointset_publish (BETA)

Check that a device publishes pointset events

1. Wait for receive a pointset event

## pointset_publish_interval (BETA)

Check handling of sample_rate_sec and sample_limit_sec

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

Check that pointset state does not report an unconfigured point

1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value
1. Update config before pointset status does not contain removed point:
    * Remove `pointset.points[random_point]`
1. Wait for pointset status does not contain removed point
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value
1. Update config before pointset status contains removed point:
    * Add `pointset.points[random_point]` = point configuration
1. Wait for pointset status contains removed point
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value

## pointset_request_extraneous (BETA)

Check error when pointset configuration contains extraneous point

1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value
1. Update config before pointset status contains extraneous point error:
    * Add `pointset.points[extraneous_point]` = point configuration
1. Wait for pointset status contains extraneous point error
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value
1. Update config before pointset status removes extraneous point error:
    * Remove `pointset.points[extraneous_point]`
1. Wait for pointset status removes extraneous point error
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value

## state_make_model (BETA)

Check that a device publishes correct make and model information in state messages

1. Check that make and model in state matches make in metadata

## state_software (BETA)

Check that a device publishes correct software information in state messages

1. Check that software in metadata matches state

## system_last_update (STABLE)

Check that last_update state is correctly set in response to a config update.

1. Wait for state last_config matches first config timestamp
1. Wait for state update complete
1. Force config update to trigger another config update
1. Wait for state last_config matches new config timestamp
1. Wait for state update complete
1. Force config update to trigger another config update
1. Wait for state last_config matches last config timestamp
1. Wait for state update complete

## valid_serial_no (BETA)

1. Wait for received serial number matches
