[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Compliance](#)

# UDMI Compliance

This is an overview of what it means for a device to be "UDMI Compliant."
There are several different facets to UDMI, each providing a different
bit of functionality, so the actual requirements will depend on the
intended function of the device.

* [_pointset_ telemetry](../messages/pointset.md), used for basic telemetry ingestion.
* [_writeback_ control](./sequences/writeback.md), used for on-prem device control.
* [_gateway_ proxy](gateway.md), used to proxy data for non-MQTT devices.
* [_system_ basics](../messages/system.md), used for general monitoring and logging.

The [Tech Primer](../tech_primer.md) gives a primer for smart-ready building assembly requirements

## Feature Buckets

The _feature buckets_ list classifies the defined functionality within the UDMI specification into
individual features and buckets. This aids in assessing levels or completeness of compliance of a
device and can be used for defining requirements.

*   **Software Defined Building**
    *   **Native UDMI**
        *   `Payloads` - Native configuration of UDMI payloads - not dependant on manual creation of MQTT message structures
        *   `Dynamic Point Mapping` - Dynamic configuration of points via config 
*   **Connection**
    *   `MQTT 3.1.1 support` - Device supports MQTT 3.1.1
    *   `MQTT/TLS Support` - Device supports connection to an MQTT broker with TLS encryption and at least TLS v1.2 
    *   `Server certificate validation` - Device validates MQTT broker server certificates
    *   `JWT Certificates` - Device supports use of JWT for authentication with an MQTT broker
    *   `GCP IoT Core support` - Device is able to successfully connect to  GCP IoT Core
    *   `Maintains Connection` - Device maintains connection to MQTT Broker/Bridger for > {X} minutes
    *   `Network resumption reconnection` - Device reconnects to MQTT broker when network connection is restored after a disruption
*   **Endpoint**
    *   `Configurable private keys` - Possible to upload private keys onto the device for MQTT authentication
    *   `Client certificate Rotation` - Device can rotate between multiple private keys to use for MQTT broker connection
    *   `Endpoint remote configuration ` - Device can be remotely reconfigured to a different GCP Project/MQTT Broker 
    *   `Config subscription` - Device subscribes to config topic 
*   **Pointset**
    *   **Datapoints mapping**
        *   `Datapoint mapping` - Map internal data points to UDMI data points
    *   **Pointset Event**
        *   `Event Publish` - Publishes pointset event messages
        *   `sample_rate_sec` - Valid event payload schema, with complete pointset sent within the sample_rate_min time period
        *   `Frequency` - Telemetry (complete update) sent at a frequency > {X}s
        *   `Configurable sample rate` - Implements sample_limit_sec and sample_rate_sec
        *   **Partial Updates**
            *   `Partial updates` - Supports partial updates (with partial_update flag set to true)
        *   **CoV**
            *   `Supports CoV` - Device supports CoV
            *   `Configurable CoV Increment` - Configurable CoV increment from cloud config
    *   **State**
        *   `State publish` - Publishes state messages
        *   `Schema` - Valid state payload schema sent by device (individual, gateway and proxied devices) including complete pointset
        *   `Frequency` - State update sent at a frequency > {X}s
        *   `Rate Limiting` - Device publishes state no more than 1 state update per second
        *   `State after configuration` - Device publishes state update after receiving new configuration
        *   `State last update` - last_update field in state is timestamp of last configuration 
*   **Monitoring**
    *   **System Status**
        *   `Publishes status` - Device publishes status fields 
        *   `status schema` - Status blocks are valid according to the schema
    *   `min_loglevel` - Configurable min log level for publishing status/logging information
    *   **Log entries**
        *   `system/logentry` - Device publishes log entries to system/log entry
        *   `logentry schema` - Log entries are valid according to the schema
*   **Gateway**
    *   `IoT Gateway` - Device capable of acting as a IoT gateway and can attach to at least one proxy device
    *   `Device Errors` - Reports device errors in gateway state message
*   **Writeback**
    *   `Basic Writeback` - Device implements basic writeback functionality
        *   **Successful Writeback**
            *   `Value state applied` - point state in state message set to applied
            *   `Point value updated` - point value updated in telemetry
        *   **Unwritable and overridden points**
            *   `Value not applied` - points which are unwritable or overridden are not updated and state is set to failure
            *   **Status**
                *   `state.pointset.points.config.failure` - point status for failure to apply
        *   **Invalid writeback**
            *   `Value not applied` - Invalid writeback (e.g. out of range) is reported
            *   **Status**
                *   `state.pointset.points.config.invalid` - point status for invalid writeback
    *   **State etag**
        *   `State etags` - Device implements state etags and rejects config updates with invalid etags
        *   **Status**
            *   **state.pointset.points.config.invalid**
    *   **Config Expiry**
        *   `Config Expiry` - Device implements configuration expiry
        *   **Status**
            *   `state.pointset.points.config.invalid` - status for invalid expiry

## Testing

The [validator](../tools/validator.md) and [sequencer](../tools/sequencer.md) tools can be used to
validate conformance to the schema.

The [registar](../tools/registrar.md#tool-execution) tool can be used to validate
[metadata](metadata.md) and [site models](site_model.md).

[DAQ](https://github.com/faucetsdn/daq) can be used to run some automated [UDMI
tests](https://github.com/faucetsdn/daq/blob/master/docs/cloud_tests.md) on devices
