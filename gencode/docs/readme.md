[**UDMI**](../../) / [Schema](#)

<!-- Template for gencode/docs/readme.md populated by bin/gendocs -->
<!-- Second level headings correspond to Section ($section) in schema (case-sensitive)-->

# UDMI Schema 

## Messages
* [**config**](config.html) - The config block controls a device's intended behavior. [Config Documentation](../../docs/messages/config.md)
* [**event_discovery**](event_discovery.html) - [Discovery result](../../docs/specs/discovery.md) with implicit enumeration
* [**event_pointset**](event_pointset.html) - A set of points reporting telemetry data. [Pointset Event Documentation](../../docs/messages/pointset.md#telemetry)
* [**event_system**](event_system.html) - Used for system events such as logging. [System Event Documentation](../../docs/messages/system.md#event)
* [**state**](state.html) - [State](../../docs/messages/state.md) message, defined by [`state.json`]

## Site Model
* [**metadata**](metadata.html) - [Metadata](../../docs/specs/metadata.md) is a description about the device: a specification about how the device should be configured and expectations about what the device should be doing. Defined by `metadata.json`

## Blobs
* [**configuration_endpoint**](configuration_endpoint.html) - Parameters to define a message endpoint

## Other
* [**command_discovery**](command_discovery.html) - [Discovery command](../../docs/specs/discovery.md) for provisioning
* [**command_mapping**](command_mapping.html) - [Mapping command](../../docs/specs/mapping.md) for provisioning
* [**config_mapping**](config_mapping.html) - Configuration for [mapping](../../docs/specs/mapping.md)
* [**configuration_execution**](configuration_execution.html) - Parameters for configuring the execution run of a UDMI tool
* [**configuration_pod**](configuration_pod.html) - Parameters for configuring the execution run of a UDMIS pod
* [**configuration_pubber**](configuration_pubber.html) - Parameters to define a pubber runtime instance
* [**event**](event.html) - Container object for all event schemas, not directly used.
* [**event_mapping**](event_mapping.html) - [Mapping result](../../docs/specs/mapping.md) with implicit enumeration
* [**event_validation**](event_validation.html) - Validation device result
* [**monitoring**](monitoring.html) - Output from UDMIS monitoring
* [**persistent_device**](persistent_device.html) - Device persistent data
* [**properties**](properties.html)
* [**state_mapping**](state_mapping.html) - State for [mapping](../../docs/specs/mapping.md)
* [**state_udmi**](state_udmi.html) - State of a UDMI reflector client
* [**state_validation**](state_validation.html) - Validation state summary
