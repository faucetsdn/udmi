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

*   **Native UDMI**
    *   _`Payloads`_ - Native configuration of UDMI payloads - not dependant on manual creation of MQTT message structures
*   **Connection**
    *   _`MQTT 3.1.1 support`_ - Device supports MQTT 3.1.1
    *   _`MQTT/TLS Support`_ - Device supports connection to an MQTT broker with TLS encryption and at least TLS v1.2
    *   _`Server certificate validation`_ - Device validates MQTT broker server certificates
    *   _`Maintains Connection`_ - The device can connect and sustain a connection to the MQTT broker
    *   _`Network resumption reconnection`_ - Device reconnects to MQTT broker when connection is lost, or reattempts connection if connection attempts are unsuccessful
    *   _`Exponential backoff`_ - Device reconnects to MQTT broker when connection is lost, or reattempts connection if connection attempts are unsuccessful
    *   **Authentication**
        *   _`JWT `_ - Device is able to authenticate to an MQTT bridge using JWT credentials in the password and renew
*   **Endpoint**
    *   _`User configurable connection`_ - Connection parameters (including client ID, hostname, port, etc) are user configurable
    *   _`Configurable private keys`_ - Possible to upload private keys onto the device for MQTT authentication
    *   _`Device generated keys`_ - Possible to generate private/public key pair on the device and to download the public key
    *   _`Client certificate rotation`_ - Device can rotate between multiple private keys to use for MQTT broker connection
    *   _`Endpoint remote configuration `_ - Device can be remotely reconfigured to a different GCP Project/MQTT Broker
    *   **Reconfiguration**
        *   _`Hostname`_ - The device implements UDMI endpoint reconfiguration, and can receive a new hostname for the MQTT endpoint configuration
        *   _`Port`_ - The device implements UDMI endpoint reconfiguration, and can receive a new port for the MQTT endpoint configuration
        *   _`Client ID`_ - The device implements UDMI endpoint reconfiguration, and can receive a new MQTT client ID
        *   _`Invalid endpoint`_ - If the device receives an invalid endpoint configuration, it continues working with the original endpoint
        *   _`Comit new endpoint`_ - The device commits new endpoint configuration to permanent memory and uses the new configuration after a reboot
    *   **Config**
        *   _`Config subscription`_ - Device subscribes to the config topic
        *   _`Config PUBACK`_ - Device subscribes to the config topic with a QoS 1 and always publishes PUBACKs to the broker
        *   _`Config message parsing`_ - Config messages are parsed with expected behavior, including when there are erroneous fields
        *   _`State after configuration`_ - Device publishes state messages after receiving a config message
        *   _`Last Update`_ - system.last_update field in state messages matches timestamp of last config message
        *   _`Broken config handling`_ - The device remains functional when receiving a config messages which is invalid at a core level (e.g. invalid JSON, missing fields required fields such as top level timestamp), retaining the previous config
        *   _`Extra field in config handling`_ - The device remains functional when config messages contains fields which are not recognized by the device
        *   **Logging**
            *   _`Config Receive`_ - Device publishes a logentry when it receives a config update
            *   _`Config Parse`_ - Device publishes a logentry when it parses a config update
            *   _`Config Apply`_ - Device publishes a logentry when it applies (accepts) a config update
    *   **State**
        *   _`Schema`_ - State message payload schema conforms with schema for state messages
        *   _`Rate Limiting`_ - Device publishes state no more than 1 state update per second
        *   _`Hardware`_ - Hardware block is included and the contents match the physical hardware
        *   _`Software`_ - Software block in state messages is included, with keys and values automatically generated by the device matching the software versions installed
        *   _`Serial Number`_ - Serial number is included and matches physical hardware
*   **Pointset**
    *   **Config**
        *   _`Datapoint mapping`_ - Mapping of UDMI data points to device-internal data points is through config messages
        *   _`Pointset definition`_ - Device pointset (data points for which the device publishes data) is configured from config message
    *   **Pointset Event**
        *   _`Publish`_ - Publishes pointset event messages
        *   _`Schema`_ - Message complies with schema (event.json) with complete pointset
        *   _`Offline buffering`_ - Telemetry events while device is offline are stored and published when connection is resumed
        *   _`sample_rate_sec`_ - Sampling rate for the system, at which it should proactively send a complete pointset within the `pointset.sample_rate_min`, defined in the config message
        *   _`sample_limit_sec`_ - Minimum time between pointset events is defined by `pointset.sample_limit_sec` in the config message
        *   **Partial Updates**
            *   _`Partial updates`_ - Supports partial updates (with partial_update flag set to true)
        *   **CoV**
            *   _`Supports CoV`_ - Device supports CoV
            *   _`Configurable CoV Increment`_ - Configurable CoV increment per point through UDMI Config
    *   **State**
        *   _`Pointset in state`_ - Pointset block is included in state messages
*   **Monitoring**
    *   **Config**
        *   _`Minimum loglevel `_ - Configurable min log level for filtering published status/logging information
    *   **Logging**
        *   _`Buffered`_ - Device buffers messages, including those whilst the device is offline or before the UDMI application is initialized
        *   _`system/logentry`_ - Device publishes log entries to system/log entry
        *   _`logentry schema`_ - Log entries are valid according to the schema
        *   **Log Entries**
            *   _`system.base.startup`_ - System startup events are logged
            *   _`system.base.ready`_ - System fully-ready events are logged
            *   _`system.base.shutdown`_ - System shutdown events are logged
            *   _`system.network.connect`_ - Network connection events are logged
            *   _`system.network.disconnect`_ - Network disconnection events are logged
            *   _`system.auth.login`_ - Successful logins to device application are logged
            *   _`system.auth.fail`_ - Failed authentication attempts to device application are logged
            *   _`System errors`_ - Publishes system errors
    *   **Metrics**
        *   _`publish`_ - Device publishes system metrics
        *   _`schema`_ - metrics valid with UDMI schema
        *   _`metrics_rate_sec`_ - Implements metrics_rate_sec to configure sample rate for metrics
*   **Gateway**
    *   _`Attachment gateway`_ - Device capable of acting as an attachment gateway and can attach to at least one proxy device
    *   **State**
        *   _`Device Errors`_ - Reports device errors in gateway state message
*   **Writeback**
    *   **Successful Writeback**
        *   _`Point state applied`_ - point state in state message set to applied
        *   _`Point value updated`_ - point value updated in telemetry
    *   **Overridden points**
        *   _`Point state overridden`_ - point state in state message set to overridden
        *   _`status pointset.point.overridden`_ - point status for failure to apply
        *   _`Active overridden detection`_ - Actively detects when a point is overridden
    *   **Unsuccessful Writeback**
        *   _`Invalid Value`_ - Point state set to invalid when writeback fails and the device knows it was because the device was invalid (e.g. out of acceptable range, type)
        *   _`Unsuccessful value state`_ - Point state set to invalid or failure for unsuccessful writeback (e.g. out of range or unwriteable)
        *   _`Status Field`_ - point status for invalid writeback
    *   **State etag**
        *   _`State etags`_ - Device implements state etags and rejects config updates with invalid etags
        *   _`state etag publish`_ - Device publishes state etags
        *   _`etag status`_ - Device publishes status when etag is rejected
    *   **Config Expiry**
        *   _`Config Expiry`_ - Device implements configuration expiry
        *   _`state.pointset.points.config.invalid`_ - Pointset status for invalid config
*   **Discovery**
    *   **Discovery Events**
        *   _`Valid Schema`_ - Discovery event messages have valid schema
    *   **Scanning**
        *   **Scan modes**
            *   _`Sporadic`_ - Supports sporadic scanning mode
            *   _`Periodic`_ - Supports periodic scanning mode
            *   _`Continuous`_ - Supports continuous scanning mode
    *   **Enumeration**
        *   **Self enumeration**
    *   **Protocols**
        *   _`IP `_ - Supports scanning IP
        *   _`Bacnet`_ - Supports scanning bacnet

## Testing

The [validator](../tools/validator.md) and [sequencer](../tools/sequencer.md) tools can be used to
validate conformance to the schema.

The [registrar](../tools/registrar.md#tool-execution) tool can be used to validate
[metadata](metadata.md) and [site models](site_model.md).
