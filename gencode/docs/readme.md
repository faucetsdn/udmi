[**UDMI**](../../) / [Schema](#)

# UDMI Schema 

## Messages
* [**config**](config.html) - The config block controls a device's intended behavior. [Config Documentation](../../docs/messages/config.md)
* [**envelope**](envelope.html) - The UDMI `envelope` is not a message itself, per se, but the attributes and other information that is delivered along with a message. [Message Envelope Documentation](../../docs/messages/envelope.md)
* [**event_discovery**](event_discovery.html) - [Discovery result](../../docs/specs/discovery.md) with implicit enumeration
* [**event_pointset**](event_pointset.html) - A set of points reporting telemetry data. [Pointset Event Documentation](../../docs/messages/pointset.md#telemetry)
* [**state**](state.html) - [State](../../docs/messages/state.md) message, defined by [`state.json`]

## Site Model
* [**cloud_iot_config**](cloud_iot_config.html) - Parameters for configuring a connection to cloud systems
* [**metadata**](metadata.html) - [Metadata](../../docs/specs/metadata.md) is a description about the device: a specification about how the device should be configured and expectations about what the device should be doing. Defined by `metadata.json`

## Other
* [**command_discovery**](command_discovery.html) - [Discovery command](../../docs/specs/discovery.md) for provisioning
* [**command_mapping**](command_mapping.html) - [Mapping command](../../docs/specs/mapping.md) for provisioning
* [**config_mapping**](config_mapping.html) - Configuration for [mapping](../../docs/specs/mapping.md)
* [**configuration_pubber**](configuration_pubber.html) - Parameters to define a pubber runtime instance
* [**event**](event.html) - Container object for all event schemas, not directly used.
* [**event_mapping**](event_mapping.html) - [Mapping result](../../docs/specs/mapping.md) with implicit enumeration
* [**event_system**](event_system.html) - Used for system events such as logging. [System Event Documentation](../../docs/messages/system.md#event)
* [**event_validation**](event_validation.html) - Validation device result
* [**properties**](properties.html)
* [**reflect_config**](reflect_config.html) - Config for a reflector client
* [**reflect_state**](reflect_state.html) - State of a reflector client
* [**state_mapping**](state_mapping.html) - State for [mapping](../../docs/specs/mapping.md)
* [**state_validation**](state_validation.html) - Validation state summary
