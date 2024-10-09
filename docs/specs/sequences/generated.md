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
* [bad_point_ref](#bad_point_ref-preview): Error handling for badly formed gateway point ref
* [bad_target_address](#bad_target_address-preview): Error handling for badly formed gateway target address
* [bad_target_family](#bad_target_family-preview): Error handling for badly formed gateway target family
* [broken_config](#broken_config-stable): Check that the device correctly handles a broken (non-json) config message.
* [config_logging](#config_logging-stable): Check that the device publishes minimum required log entries when receiving config
* [device_config_acked](#device_config_acked-stable): Check that the device MQTT-acknowledges a sent config.
* [empty_enumeration](#empty_enumeration-preview): Check enumeration of nothing at all
* [endpoint_connection_error](#endpoint_connection_error-preview): Push endpoint config message to device that results in a connection error.
* [endpoint_connection_retry](#endpoint_connection_retry-preview): Check repeated endpoint with same information gets retried.
* [endpoint_connection_success_alternate](#endpoint_connection_success_alternate-preview): Check connection to an alternate project.
* [endpoint_connection_success_reconnect](#endpoint_connection_success_reconnect-preview): Check a successful reconnect to the same endpoint.
* [endpoint_failure_and_restart](#endpoint_failure_and_restart-preview)
* [endpoint_redirect_and_restart](#endpoint_redirect_and_restart-preview)
* [extra_config](#extra_config-stable): Check that the device correctly handles an extra out-of-schema field
* [family_ether_addr](#family_ether_addr-preview)
* [family_ipv4_addr](#family_ipv4_addr-preview)
* [family_ipv6_addr](#family_ipv6_addr-preview)
* [feature_enumeration](#feature_enumeration-preview): Check enumeration of device features
* [gateway_proxy_events](#gateway_proxy_events-beta): Check that a gateway proxies pointset events for indicated devices
* [gateway_proxy_state](#gateway_proxy_state-preview): Check that a gateway proxies state updates for indicated devices
* [pointset_publish](#pointset_publish-stable): Check that a device publishes pointset events
* [pointset_publish_interval](#pointset_publish_interval-stable): Check handling of sample_rate_sec and sample_limit_sec
* [pointset_remove_point](#pointset_remove_point-stable): Check that pointset state does not report an unconfigured point
* [pointset_request_extraneous](#pointset_request_extraneous-stable): Check error when pointset configuration contains extraneous point
* [state_make_model](#state_make_model-stable): Check that a device publishes correct make and model information in state messages
* [state_software](#state_software-stable): Check that a device publishes correct software information in state messages
* [system_last_update](#system_last_update-stable): Check that last_update state is correctly set in response to a config update.
* [valid_serial_no](#valid_serial_no-stable)

## bad_point_ref (PREVIEW)

Error handling for badly formed gateway point ref

1. Test skipped: Not a proxied device

## bad_target_address (PREVIEW)

Error handling for badly formed gateway target address

1. Test skipped: Not a proxied device

## bad_target_family (PREVIEW)

Error handling for badly formed gateway target family

1. Test skipped: Not a proxied device

## broken_config (STABLE)

Check that the device correctly handles a broken (non-json) config message.

1. Wait for config sync
1. Update config starting broken_config:
    * Set `system.min_loglevel` = `100`
1. Wait for initial state synchronized
1. Check that initial stable_config matches last_config
1. Wait for log category `system.config.apply` level `NOTICE` to be logged
1. Wait for config sync
1. Wait for has significant system status
1. Check that significant system status exists
1. Wait for log category `system.config.receive` level `DEBUG` to be logged
1. Wait for log category `system.config.parse` level `ERROR` to be logged
1. Check that log category `system.config.apply` level `NOTICE` not logged
1. Force reset config
1. Wait for config sync
1. Wait for log category `system.config.apply` level `NOTICE` to be logged
1. Wait for restored state synchronized
1. Wait for config sync
1. Update config before last_config updated:
    * Set `system.min_loglevel` = `100`
1. Wait for last_config updated
1. Wait for log category `system.config.apply` level `NOTICE` to be logged
1. Check that log category `system.config.receive` level `DEBUG` not logged
1. Check that log category `system.config.parse` level `DEBUG` not logged

## config_logging (STABLE)

Check that the device publishes minimum required log entries when receiving config

1. Wait for config sync
1. Update config set min_loglevel to debug:
    * Set `system.min_loglevel` = `100`
1. Wait for config sync
1. Force config update to resend config to device
1. Wait for log category `system.config.receive` level `DEBUG` to be logged
1. Wait for log category `system.config.parse` level `DEBUG` to be logged
1. Wait for log category `system.config.apply` level `NOTICE` to be logged

## device_config_acked (STABLE)

Check that the device MQTT-acknowledges a sent config.

1. Wait for config sync
1. Wait for config acked

## empty_enumeration (PREVIEW)

Check enumeration of nothing at all

1. Wait for config sync
1. Update config before enumeration not active:
    * Add `discovery` = { "depths": {  } }
1. Wait for enumeration not active
1. Wait for config sync
1. Update config before matching enumeration generation:
    * Add `discovery.generation` = `generation start time`
1. Wait for matching enumeration generation
1. Wait for config sync
1. Update config before cleared enumeration generation:
    * Remove `discovery.generation`
1. Wait for cleared enumeration generation
1. Check that no family enumeration exists
1. Check that no feature enumeration exists
1. Check that no point enumeration exists

## endpoint_connection_error (PREVIEW)

Push endpoint config message to device that results in a connection error.

1. Wait for config sync
1. Update config before blobset entry config status is error:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset entry config status is error
1. Wait for config sync
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

## endpoint_connection_retry (PREVIEW)

Check repeated endpoint with same information gets retried.

1. Wait for config sync
1. Update config before blobset entry config status is error:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset entry config status is error
1. Wait for config sync
1. Update config before blobset entry config status is error:
    * Set `blobset.blobs._iot_endpoint_config.generation` = `new generation`
1. Wait for blobset entry config status is error
1. Wait for config sync
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

## endpoint_connection_success_alternate (PREVIEW)

Check connection to an alternate project.

1. Wait for config sync
1. Wait for initial last_config matches config timestamp
1. Wait for config sync
1. Update config mirroring config false:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset phase is apply and stateStatus is null
1. Wait for config sync
1. Wait for blobset phase is final and stateStatus is null
1. Wait for alternate last_config matches config timestamp
1. Wait for config sync
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
1. Wait for config sync
1. Update config mirroring config true:
    * Add `blobset.blobs._iot_endpoint_config` = { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` }
1. Wait for blobset phase is apply and stateStatus is null
1. Wait for config sync
1. Wait for blobset phase is final and stateStatus is null
1. Wait for restored last_config matches config timestamp
1. Wait for config sync
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

## endpoint_connection_success_reconnect (PREVIEW)

Check a successful reconnect to the same endpoint.

1. Wait for config sync
1. Update config before blobset phase is final and stateStatus is null:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset phase is final and stateStatus is null
1. Wait for config sync
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

## endpoint_failure_and_restart (PREVIEW)

1. Wait for config sync
1. Update config before blobset entry config status is error:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset entry config status is error
1. Wait for last_start is not zero
1. Check that initial count is greater than 0
1. Wait for config sync
1. Update config before system mode is ACTIVE:
    * Add `system.operation.mode` = `active`
1. Wait for system mode is ACTIVE
1. Wait for config sync
1. Update config before system mode is INITIAL:
    * Set `system.operation.mode` = `restart`
1. Wait for system mode is INITIAL
1. Check that restart count increased by one
1. Wait for config sync
1. Update config before system mode is ACTIVE:
    * Set `system.operation.mode` = `active`
1. Wait for system mode is ACTIVE
1. Wait for config sync
1. Wait for last_config is newer than previous last_config before abort
1. Wait for last_config is newer than previous last_config after abort
1. Wait for last_start is newer than previous last_start
1. Wait for config sync
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

## endpoint_redirect_and_restart (PREVIEW)

1. Wait for config sync
1. Wait for initial last_config matches config timestamp
1. Wait for config sync
1. Update config mirroring config false:
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset phase is apply and stateStatus is null
1. Wait for config sync
1. Wait for blobset phase is final and stateStatus is null
1. Wait for alternate last_config matches config timestamp
1. Wait for config sync
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
1. Wait for last_start is not zero
1. Check that initial count is greater than 0
1. Wait for config sync
1. Update config before system mode is ACTIVE:
    * Add `system.operation.mode` = `active`
1. Wait for system mode is ACTIVE
1. Wait for config sync
1. Update config before system mode is INITIAL:
    * Set `system.operation.mode` = `restart`
1. Wait for system mode is INITIAL
1. Check that restart count increased by one
1. Wait for config sync
1. Update config before system mode is ACTIVE:
    * Set `system.operation.mode` = `active`
1. Wait for system mode is ACTIVE
1. Wait for config sync
1. Wait for last_config is newer than previous last_config before abort
1. Wait for last_config is newer than previous last_config after abort
1. Wait for last_start is newer than previous last_start
1. Wait for config sync
1. Update config mirroring config true:
    * Add `blobset.blobs._iot_endpoint_config` = { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` }
1. Wait for blobset phase is apply and stateStatus is null
1. Wait for config sync
1. Wait for blobset phase is final and stateStatus is null
1. Wait for restored last_config matches config timestamp
1. Wait for config sync
1. Update config before endpoint config blobset state not defined:
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

## extra_config (STABLE)

Check that the device correctly handles an extra out-of-schema field

1. Wait for config sync
1. Update config before last_config not null:
    * Set `system.min_loglevel` = `100`
1. Wait for last_config not null
1. Wait for system operational
1. Check that no significant system status exists
1. Wait for config sync
1. Wait for log category `system.config.receive` level `DEBUG` to be logged
1. Wait for last_config updated
1. Wait for system operational
1. Check that no significant system status exists
1. Wait for log category `system.config.parse` level `DEBUG` to be logged
1. Wait for log category `system.config.apply` level `NOTICE` to be logged
1. Wait for config sync
1. Wait for log category `system.config.receive` level `DEBUG` to be logged
1. Wait for last_config updated again
1. Wait for system operational
1. Wait for log category `system.config.parse` level `DEBUG` to be logged
1. Wait for log category `system.config.apply` level `NOTICE` to be logged

## family_ether_addr (PREVIEW)

1. Wait for config sync
1. Wait for localnet family state ether available
1. Check that family ether address matches

## family_ipv4_addr (PREVIEW)

1. Wait for config sync
1. Wait for localnet family state ipv4 available
1. Check that family ipv4 address matches

## family_ipv6_addr (PREVIEW)

1. Wait for config sync
1. Wait for localnet family state ipv6 available
1. Check that family ipv6 address matches

## feature_enumeration (PREVIEW)

Check enumeration of device features

1. Wait for config sync
1. Update config before enumeration not active:
    * Add `discovery` = { "depths": { "features": `entries` } }
1. Wait for enumeration not active
1. Wait for config sync
1. Update config before matching enumeration generation:
    * Add `discovery.generation` = `generation start time`
1. Wait for matching enumeration generation
1. Wait for config sync
1. Update config before cleared enumeration generation:
    * Remove `discovery.generation`
1. Wait for cleared enumeration generation
1. Check that no family enumeration exists
1. Check that feature enumeration matches metadata
1. Check that all enumerated features are official buckets
1. Check that no point enumeration exists

## gateway_proxy_events (BETA)

Check that a gateway proxies pointset events for indicated devices

1. Test skipped: Not a gateway

## gateway_proxy_state (PREVIEW)

Check that a gateway proxies state updates for indicated devices

1. Test skipped: Not a gateway

## pointset_publish (STABLE)

Check that a device publishes pointset events

1. Wait for config sync
1. Wait for receive a pointset event

## pointset_publish_interval (STABLE)

Check handling of sample_rate_sec and sample_limit_sec

1. Wait for config sync
1. Update config before receive at least 4 pointset events:
    * Add `pointset.sample_rate_sec` = `8`
    * Add `pointset.sample_limit_sec` = `5`
1. Wait for receive at least 4 pointset events
1. Check that time period between successive pointset events is between 5 and 8 seconds
1. Wait for config sync
1. Update config before receive at least 4 pointset events:
    * Set `pointset.sample_rate_sec` = `18`
    * Set `pointset.sample_limit_sec` = `15`
1. Wait for receive at least 4 pointset events
1. Check that time period between successive pointset events is between 15 and 18 seconds

## pointset_remove_point (STABLE)

Check that pointset state does not report an unconfigured point

1. Wait for config sync
1. Wait for pointset state matches config
1. Wait for pointset event contains correct points
1. Wait for config sync
1. Update config before pointset state does not contain removed point:
    * Remove `pointset.points[random_point]`
1. Wait for pointset state does not contain removed point
1. Wait for pointset state matches config
1. Wait for pointset event contains correct points
1. Wait for config sync
1. Update config before pointset state contains restored point:
    * Add `pointset.points[random_point]` = point configuration
1. Wait for pointset state contains restored point
1. Wait for pointset state matches config
1. Wait for pointset event contains correct points

## pointset_request_extraneous (STABLE)

Check error when pointset configuration contains extraneous point

1. Wait for config sync
1. Update config Before pointset state matches config:
    * Add `pointset.sample_rate_sec` = `10`
1. Wait for pointset state matches config
1. Wait for pointset event contains correct points
1. Wait for config sync
1. Update config before pointset state contains extraneous point error:
    * Add `pointset.points[extraneous_point]` = point configuration
1. Wait for pointset state contains extraneous point error
1. Wait for pointset state matches config
1. Wait for pointset event contains correct points
1. Wait for config sync
1. Update config before pointset state removes extraneous point error:
    * Remove `pointset.points[extraneous_point]`
1. Wait for pointset state removes extraneous point error
1. Wait for pointset state matches config
1. Wait for pointset event contains correct points

## state_make_model (STABLE)

Check that a device publishes correct make and model information in state messages

1. Check that make and model in state matches make in metadata

## state_software (STABLE)

Check that a device publishes correct software information in state messages

1. Check that software in metadata matches state

## system_last_update (STABLE)

Check that last_update state is correctly set in response to a config update.

1. Wait for config sync
1. Wait for state last_config matches config timestamp
1. Wait for state update complete
1. Wait for config sync
1. Force config update to trigger another config update
1. Wait for state last_config matches config timestamp
1. Wait for state update complete
1. Wait for config sync
1. Force config update to trigger another config update
1. Wait for state last_config matches config timestamp
1. Wait for state update complete

## valid_serial_no (STABLE)

1. Wait for config sync
1. Wait for received serial number matches
