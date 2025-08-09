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
* [bad_point_ref](#bad_point_ref-preview): Error handling for badly formed gateway point ref Test skipped: Not a proxied device
* [bad_target_address](#bad_target_address-preview): Error handling for badly formed gateway target address Test skipped: Not a proxied device
* [bad_target_family](#bad_target_family-preview): Error handling for badly formed gateway target family Test skipped: Not a proxied device
* [broken_config](#broken_config-stable): Check that the device correctly handles a broken (non-json) config message.
* [config_logging](#config_logging-stable): Check that the device publishes minimum required log entries when receiving config
* [device_config_acked](#device_config_acked-stable): Check that the device MQTT-acknowledges a sent config.
* [endpoint_connection_bad_alternate](#endpoint_connection_bad_alternate-preview): Failed connection never uses alternate registry.
* [endpoint_connection_bad_hash](#endpoint_connection_bad_hash-preview): Failed connection because of bad hash.
* [endpoint_connection_error](#endpoint_connection_error-preview): Push endpoint config message to device that results in a connection error.
* [endpoint_connection_retry](#endpoint_connection_retry-preview): Check repeated endpoint with same information gets retried.
* [endpoint_connection_success_alternate](#endpoint_connection_success_alternate-preview): Check connection to an alternate project.
* [endpoint_connection_success_reconnect](#endpoint_connection_success_reconnect-preview): Check a successful reconnect to the same endpoint.
* [endpoint_failure_and_restart](#endpoint_failure_and_restart-preview)
* [endpoint_redirect_and_restart](#endpoint_redirect_and_restart-preview)
* [enumerate_families](#enumerate_families-preview): Check enumeration of network families
* [enumerate_features](#enumerate_features-preview): Check enumeration of device features
* [enumerate_multi](#enumerate_multi-preview): Check enumeration of multiple categories
* [enumerate_nothing](#enumerate_nothing-preview): Check enumeration of nothing at all
* [enumerate_pointset](#enumerate_pointset-preview): Check enumeration of device points
* [extra_config](#extra_config-stable): Check that the device correctly handles an extra out-of-schema field
* [family_ether_addr](#family_ether_addr-preview)
* [family_ipv4_addr](#family_ipv4_addr-preview)
* [family_ipv6_addr](#family_ipv6_addr-preview): Test skipped: No ipv6 address defined in metadata
* [gateway_proxy_events](#gateway_proxy_events-beta): Check that a gateway proxies pointset events for indicated devices Test skipped: Not a gateway
* [gateway_proxy_state](#gateway_proxy_state-preview): Check that a gateway proxies state updates for indicated devices Test skipped: Not a gateway
* [pointset_publish](#pointset_publish-stable): Check that a device publishes pointset events
* [pointset_publish_interval](#pointset_publish_interval-stable): Check handling of sample_rate_sec and sample_limit_sec
* [pointset_remove_point](#pointset_remove_point-stable): Check that pointset state does not report an unconfigured point
* [pointset_request_extraneous](#pointset_request_extraneous-stable): Check error when pointset configuration contains extraneous point
* [scan_periodic_now_enumerate](#scan_periodic_now_enumerate-preview): Check periodic scan on a fixed schedule and enumeration
* [scan_single_future](#scan_single_future-preview): Check results of a single scan scheduled soon
* [scan_single_now](#scan_single_now-preview): Check results of a single scan scheduled in the recent past including enumeration
* [scan_single_past](#scan_single_past-preview): Check that a scan scheduled in the past never starts
* [state_make_model](#state_make_model-stable): Check that a device publishes correct make and model information in state messages
* [state_software](#state_software-stable): Check that a device publishes correct software information in state messages
* [system_last_update](#system_last_update-stable): Check that last_update state is correctly set in response to a config update.
* [system_mode_restart](#system_mode_restart-preview): Restart and connect to same endpoint and expect it returns.
* [valid_serial_no](#valid_serial_no-stable)

## bad_point_ref (PREVIEW)

Error handling for badly formed gateway point ref


Test skipped: Not a proxied device

## bad_target_address (PREVIEW)

Error handling for badly formed gateway target address


Test skipped: Not a proxied device

## bad_target_family (PREVIEW)

Error handling for badly formed gateway target family


Test skipped: Not a proxied device

## broken_config (STABLE)

Check that the device correctly handles a broken (non-json) config message.

1. Update config to enable debug logging
    * Set `system.min_loglevel` = `100`
1. _logging_ Wait until system logs level `NOTICE` category `system.config.apply`
1. Update config to force broken (invalid JSON) configuration
1. _status_ Wait until system status level is >= `WARNING` (400)
1. _status_ Check that status level is exactly `ERROR` (500)
1. _status_ Check that category matches `system.config.parse`
1. Check that device state `last_config` has not been updated
1. _logging_ Wait until system logs level `DEBUG` category `system.config.receive`
1. _logging_ Wait until system logs level `ERROR` category `system.config.parse`
1. _logging_ Check that log level `NOTICE` (or greater) category `system.config.apply` was not logged
1. Reset config to clean version
1. (Log level is implicitly set to `INFO` through config reset)
1. _status_ Wait until system status level is not >= `WARNING` (400)
1. _logging_ Wait until system logs level `NOTICE` category `system.config.apply`
1. _logging_ Check that log level `DEBUG` (or greater) category `system.config.receive` was not logged
1. _logging_ Check that log level `DEBUG` (or greater) category `system.config.parse` was not logged
1. Check that device state `last_config` has been updated

Test passed.

## config_logging (STABLE)

Check that the device publishes minimum required log entries when receiving config

1. Force config update to resend config to device
1. Wait until system logs level `DEBUG` category `system.config.receive`
1. Wait until system logs level `DEBUG` category `system.config.parse`
1. Wait until system logs level `NOTICE` category `system.config.apply`

Test passed.

## device_config_acked (STABLE)

Check that the device MQTT-acknowledges a sent config.

1. Wait for config acked

Test passed.

## endpoint_connection_bad_alternate (PREVIEW)

Failed connection never uses alternate registry.

1. Wait until initial last_config matches config timestamp
1. Update config before blobset phase is final and stateStatus is not null
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset phase is final and stateStatus is not null
1. Wait for blobset phase is final and stateStatus is not null
1. Wait until alternate client connect delay
1. Wait for blobset phase is final and stateStatus is null
1. Wait until restored last_config matches config timestamp
1. Update config before endpoint config blobset state not defined
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

Test passed.

## endpoint_connection_bad_hash (PREVIEW)

Failed connection because of bad hash.

1. Update config before blobset status is ERROR
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `invalid blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset status is ERROR

Test passed.

## endpoint_connection_error (PREVIEW)

Push endpoint config message to device that results in a connection error.

1. Update config before blobset entry config status is error
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset entry config status is error
1. Update config before endpoint config blobset state not defined
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

Test passed.

## endpoint_connection_retry (PREVIEW)

Check repeated endpoint with same information gets retried.

1. Update config before blobset entry config status is error
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset entry config status is error
1. Update config before blobset entry config status is error
    * Set `blobset.blobs._iot_endpoint_config.generation` = `new generation`
1. Wait for blobset entry config status is error
1. Update config before endpoint config blobset state not defined
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

Test passed.

## endpoint_connection_success_alternate (PREVIEW)

Check connection to an alternate project.

1. Wait until initial last_config matches config timestamp
1. Update config mirroring config false
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset phase is apply and stateStatus is null
1. Wait for blobset phase is final and stateStatus is null
1. Wait until alternate last_config matches config timestamp
1. Update config before endpoint config blobset state not defined
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
1. Update config mirroring config true
    * Add `blobset.blobs._iot_endpoint_config` = { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` }
1. Wait for blobset phase is apply and stateStatus is null
1. Wait for blobset phase is final and stateStatus is null
1. Wait until restored last_config matches config timestamp
1. Update config before endpoint config blobset state not defined
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

Test passed.

## endpoint_connection_success_reconnect (PREVIEW)

Check a successful reconnect to the same endpoint.

1. Update config before blobset phase is final and stateStatus is null
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset phase is final and stateStatus is null
1. Update config before endpoint config blobset state not defined
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

Test passed.

## endpoint_failure_and_restart (PREVIEW)

1. Update config before blobset entry config status is error
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset entry config status is error
1. Wait for last_start is not zero
1. Check that initial count is greater than 0
1. Update config before system mode is ACTIVE
    * Add `system.operation.mode` = `active`
1. Wait for system mode is ACTIVE
1. Update config before system mode is INITIAL
    * Set `system.operation.mode` = `restart`
1. Wait for system mode is INITIAL
1. Check that restart count increased by one
1. Update config before system mode is ACTIVE
    * Set `system.operation.mode` = `active`
1. Wait for system mode is ACTIVE
1. Wait for last_config is newer than previous last_config before abort
1. Wait for last_config is newer than previous last_config after abort
1. Wait for last_start is newer than previous last_start
1. Update config before endpoint config blobset state not defined
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

Test passed.

## endpoint_redirect_and_restart (PREVIEW)

1. Wait until initial last_config matches config timestamp
1. Update config mirroring config false
    * Add `blobset` = { "blobs": { "_iot_endpoint_config": { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` } } }
1. Wait for blobset phase is apply and stateStatus is null
1. Wait for blobset phase is final and stateStatus is null
1. Wait until alternate last_config matches config timestamp
1. Update config before endpoint config blobset state not defined
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined
1. Wait for last_start is not zero
1. Check that initial count is greater than 0
1. Update config before system mode is ACTIVE
    * Add `system.operation.mode` = `active`
1. Wait for system mode is ACTIVE
1. Update config before system mode is INITIAL
    * Set `system.operation.mode` = `restart`
1. Wait for system mode is INITIAL
1. Check that restart count increased by one
1. Update config before system mode is ACTIVE
    * Set `system.operation.mode` = `active`
1. Wait for system mode is ACTIVE
1. Wait for last_config is newer than previous last_config before abort
1. Wait for last_config is newer than previous last_config after abort
1. Wait for last_start is newer than previous last_start
1. Update config mirroring config true
    * Add `blobset.blobs._iot_endpoint_config` = { "phase": `final`, "generation": `blob generation`, "sha256": `blob data hash`, "url": `endpoint data` }
1. Wait for blobset phase is apply and stateStatus is null
1. Wait for blobset phase is final and stateStatus is null
1. Wait until restored last_config matches config timestamp
1. Update config before endpoint config blobset state not defined
    * Remove `blobset.blobs._iot_endpoint_config`
1. Wait for endpoint config blobset state not defined

Test passed.

## enumerate_families (PREVIEW)

Check enumeration of network families

1. Update config before enumeration not active
    * Add `discovery.enumerations` = { "families": `entries` }
1. Wait for enumeration not active
1. Update config before matching enumeration generation
    * Add `discovery.generation` = `generation start time`
1. Wait for matching enumeration generation
1. Update config before cleared enumeration generation
    * Remove `discovery.generation`
1. Wait for cleared enumeration generation
1. Check that family enumeration size matches
1. Check that no feature enumeration exists
1. Check that no point enumeration exists

Test passed.

## enumerate_features (PREVIEW)

Check enumeration of device features

1. Update config before enumeration not active
    * Add `discovery.enumerations` = { "features": `entries` }
1. Wait for enumeration not active
1. Update config before matching enumeration generation
    * Add `discovery.generation` = `generation start time`
1. Wait for matching enumeration generation
1. Update config before cleared enumeration generation
    * Remove `discovery.generation`
1. Wait for cleared enumeration generation
1. Check that no family enumeration exists
1. Check that feature enumeration matches metadata
1. Check that all enumerated features are official buckets
1. Check that no point enumeration exists

Test passed.

## enumerate_multi (PREVIEW)

Check enumeration of multiple categories

1. Update config before enumeration not active
    * Add `discovery.enumerations` = { "features": `details`, "families": `details`, "points": `details` }
1. Wait for enumeration not active
1. Update config before matching enumeration generation
    * Add `discovery.generation` = `generation start time`
1. Wait for matching enumeration generation
1. Update config before cleared enumeration generation
    * Remove `discovery.generation`
1. Wait for cleared enumeration generation
1. Check that family enumeration size matches
1. Check that feature enumeration matches metadata
1. Check that all enumerated features are official buckets
1. Check that enumerated point count matches

Test passed.

## enumerate_nothing (PREVIEW)

Check enumeration of nothing at all

1. Update config before enumeration not active
    * Add `discovery.enumerations` = {  }
1. Wait for enumeration not active
1. Update config before matching enumeration generation
    * Add `discovery.generation` = `generation start time`
1. Wait for matching enumeration generation
1. Update config before cleared enumeration generation
    * Remove `discovery.generation`
1. Wait for cleared enumeration generation
1. Check that no family enumeration exists
1. Check that no feature enumeration exists
1. Check that no point enumeration exists

Test passed.

## enumerate_pointset (PREVIEW)

Check enumeration of device points

1. Update config before enumeration not active
    * Add `discovery.enumerations` = { "points": `entries` }
1. Wait for enumeration not active
1. Update config before matching enumeration generation
    * Add `discovery.generation` = `generation start time`
1. Wait for matching enumeration generation
1. Update config before cleared enumeration generation
    * Remove `discovery.generation`
1. Wait for cleared enumeration generation
1. Check that no family enumeration exists
1. Check that no feature enumeration exists
1. Check that enumerated point count matches

Test passed.

## extra_config (STABLE)

Check that the device correctly handles an extra out-of-schema field

1. Wait for last_config not null
1. Wait for system operational
1. Check that system status level is not >= `WARNING` (400)
1. Update config before system logs level `DEBUG` category `system.config.receive`
1. Wait until system logs level `DEBUG` category `system.config.receive`
1. Wait until last_config updated
1. Wait for system operational
1. Check that system status level is not >= `WARNING` (400)
1. Wait until system logs level `DEBUG` category `system.config.parse`
1. Wait until system logs level `NOTICE` category `system.config.apply`
1. Update config before system logs level `DEBUG` category `system.config.receive`
1. Wait until system logs level `DEBUG` category `system.config.receive`
1. Wait for last_config updated again
1. Wait for system operational
1. Wait until system logs level `DEBUG` category `system.config.parse`
1. Wait until system logs level `NOTICE` category `system.config.apply`

Test passed.

## family_ether_addr (PREVIEW)

1. Wait until device state localnet family ether is available
1. Check that family ether address matches

Test passed.

## family_ipv4_addr (PREVIEW)

1. Wait until device state localnet family ipv4 is available
1. Check that family ipv4 address matches

Test passed.

## family_ipv6_addr (PREVIEW)


Test skipped: No ipv6 address defined in metadata

## gateway_proxy_events (BETA)

Check that a gateway proxies pointset events for indicated devices


Test skipped: Not a gateway

## gateway_proxy_state (PREVIEW)

Check that a gateway proxies state updates for indicated devices


Test skipped: Not a gateway

## pointset_publish (STABLE)

Check that a device publishes pointset events

1. Wait for receive a pointset event

Test passed.

## pointset_publish_interval (STABLE)

Check handling of sample_rate_sec and sample_limit_sec

1. Update config before receive at least 4 pointset events
    * Add `pointset.sample_rate_sec` = `8`
    * Add `pointset.sample_limit_sec` = `5`
1. Wait for receive at least 4 pointset events
1. Check that time period between successive pointset events is between 5 and 8 seconds
1. Update config before receive at least 4 pointset events
    * Set `pointset.sample_rate_sec` = `18`
    * Set `pointset.sample_limit_sec` = `15`
1. Wait for receive at least 4 pointset events
1. Check that time period between successive pointset events is between 15 and 18 seconds

Test passed.

## pointset_remove_point (STABLE)

Check that pointset state does not report an unconfigured point

1. Wait until pointset state matches config
1. Wait until pointset event contains correct points
1. Update config before pointset state does not contain removed point
    * Remove `pointset.points[random_point]`
1. Wait for pointset state does not contain removed point
1. Wait until pointset state matches config
1. Wait until pointset event contains correct points
1. Update config before pointset state contains restored point
    * Add `pointset.points[random_point]` = point configuration
1. Wait for pointset state contains restored point
1. Wait until pointset state matches config
1. Wait until pointset event contains correct points

Test passed.

## pointset_request_extraneous (STABLE)

Check error when pointset configuration contains extraneous point

1. Update config before pointset state matches config
    * Add `pointset.sample_rate_sec` = `10`
1. Wait until pointset state matches config
1. Wait until pointset event contains correct points
1. Update config before pointset state contains extraneous point error
    * Add `pointset.points[extraneous_point]` = point configuration
1. Wait for pointset state contains extraneous point error
1. Wait until pointset state matches config
1. Wait until pointset event contains correct points
1. Update config before pointset state removes extraneous point error
    * Remove `pointset.points[extraneous_point]`
1. Wait for pointset state removes extraneous point error
1. Wait until pointset state matches config
1. Wait until pointset event contains correct points

Test passed.

## scan_periodic_now_enumerate+vendor (PREVIEW)

Check periodic scan on a fixed schedule and enumeration

1. Update config before discovery families defined
    * Remove `discovery.families.vendor`
    * Remove `discovery.families.bacnet`
1. Wait for discovery families defined
1. Wait until discovery family keys match
1. Wait for no scans active
1. Update config before scan iterations
    * Add `discovery.families.vendor` = { "generation": `family generation`, "scan_interval_sec": `20`, "depth": `details`, "scan_duration_sec": `20` }
1. Wait for scan iterations
1. Check that scan did not terminate prematurely
1. Check that all events have matching refs

Test passed.

## scan_single_future+vendor (PREVIEW)

Check results of a single scan scheduled soon

1. Update config before discovery families defined
    * Remove `discovery.families.vendor`
    * Remove `discovery.families.bacnet`
1. Wait for discovery families defined
1. Wait until discovery family keys match
1. Wait for no scans active
1. Update config before scheduled scan pending
    * Add `discovery.families.vendor` = { "generation": `family generation`, "scan_duration_sec": `10` }
1. Wait until scheduled scan pending
1. Wait until scheduled scan active
1. Check that scan started at time
1. Wait until scheduled scan complete
1. Check that scan completed at time
1. Check that received expected number of discovery events
1. Check that no events have discovered refs
1. Check that discovery events were valid
1. Check that received all unique event numbers
1. Check that received proper discovery start event
1. Check that received proper last discovery event
1. Check that received proper discovery termination event
1. Check that all scan addresses are unique
1. Check that all expected addresses were found
1. Check that all expected networks were found

Test passed.

## scan_single_now+vendor (PREVIEW)

Check results of a single scan scheduled in the recent past including enumeration

1. Update config before discovery families defined
    * Remove `discovery.families.vendor`
    * Remove `discovery.families.bacnet`
1. Wait for discovery families defined
1. Wait until discovery family keys match
1. Wait for no scans active
1. Update config before scheduled scan active
    * Add `discovery.families.vendor` = { "generation": `family generation`, "depth": `details`, "scan_duration_sec": `10` }
1. Wait until scheduled scan active
1. Check that scan started at time
1. Wait until scheduled scan complete
1. Check that scan completed at time
1. Check that received expected number of discovery events
1. Check that all events have matching refs
1. Check that discovery events were valid
1. Check that received all unique event numbers
1. Check that received proper discovery start event
1. Check that received proper last discovery event
1. Check that received proper discovery termination event
1. Check that all scan addresses are unique
1. Check that all expected addresses were found
1. Check that all expected networks were found

Test passed.

## scan_single_past+vendor (PREVIEW)

Check that a scan scheduled in the past never starts

1. Update config before discovery families defined
    * Remove `discovery.families.vendor`
    * Remove `discovery.families.bacnet`
1. Wait for discovery families defined
1. Wait until discovery family keys match
1. Wait for no scans active
1. Update config before scan schedule initially not active
    * Add `discovery.families.vendor` = { "generation": `family generation`, "scan_duration_sec": `10` }
1. Wait until scan schedule initially not active
1. Wait until scan schedule still not active
1. Check that there were no received discovery events

Test passed.

## state_make_model (STABLE)

Check that a device publishes correct make and model information in state messages

1. Check that make and model in state matches make in metadata

Test passed.

## state_software (STABLE)

Check that a device publishes correct software information in state messages

1. Check that software in metadata matches state

Test passed.

## system_last_update (STABLE)

Check that last_update state is correctly set in response to a config update.

1. Wait until state last_config matches config timestamp
1. _subblocks_ Wait until state update complete
1. Force config update to trigger another config update
1. Wait until state last_config matches config timestamp
1. _subblocks_ Wait until state update complete
1. Force config update to trigger another config update
1. Wait until state last_config matches config timestamp
1. _subblocks_ Wait until state update complete

Test passed.

## system_mode_restart (PREVIEW)

Restart and connect to same endpoint and expect it returns.

1. Wait for last_start is not zero
1. Check that initial count is greater than 0
1. Update config before system mode is ACTIVE
    * Add `system.operation.mode` = `active`
1. Wait for system mode is ACTIVE
1. Update config before system mode is INITIAL
    * Set `system.operation.mode` = `restart`
1. Wait for system mode is INITIAL
1. Check that restart count increased by one
1. Update config before system mode is ACTIVE
    * Set `system.operation.mode` = `active`
1. Wait for system mode is ACTIVE
1. Wait for last_config is newer than previous last_config before abort
1. Wait for last_config is newer than previous last_config after abort
1. Wait for last_start is newer than previous last_start

Test passed.

## valid_serial_no (STABLE)

1. Wait for received serial number matches

Test passed.
