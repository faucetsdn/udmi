[**UDMI**](../../) / [Schema](#)

<!-- Template for gencode/docs/readme.md populated by bin/gendocs -->
<!-- Second level headings correspond to Section ($section) in schema (case-sensitive)-->

# UDMI Schema 

## Messages
* [**config**](config.html) - The config block controls a device's intended behavior. [Config Documentation](../../docs/messages/config.md)
* [**events_discovery**](events_discovery.html) - [Discovery result](../../docs/specs/discovery.md) with implicit discovery
* [**events_pointset**](events_pointset.html) - A set of points reporting telemetry data. [Pointset Events Documentation](../../docs/messages/pointset.md#telemetry)
* [**events_system**](events_system.html) - Used for system events such as logging. [System Event Documentation](../../docs/messages/system.md#event)
* [**events_udmi**](events_udmi.html) - Used for udmi events such as logging.
* [**state**](state.html) - [State](../../docs/messages/state.md) message, defined by [`state.json`]

## Site Model
* [**metadata**](metadata.html) - [Metadata](../../docs/specs/metadata.md) is a description about the device: a specification about how the device should be configured and expectations about what the device should be doing. Defined by `metadata.json`
* [**site_metadata**](site_metadata.html)

## Blobs
* [**configuration_endpoint**](configuration_endpoint.html) - Parameters to define a message endpoint

## Other
* [**building_config_entity**](building_config_entity.html)
* [**commands_discovery**](commands_discovery.html) - [Discovery command](../../docs/specs/discovery.md) for provisioning
* [**commands_mapping**](commands_mapping.html) - [Mapping command](../../docs/specs/mapping.md) for provisioning
* [**configuration_execution**](configuration_execution.html) - Parameters for configuring the execution run of a UDMI tool
* [**configuration_pod**](configuration_pod.html) - Parameters for configuring the execution run of a UDMIS pod
* [**configuration_pubber**](configuration_pubber.html) - Parameters to define a pubber runtime instance
* [**data_template**](data_template.html) - Information container for simple template substitution.
* [**events**](events.html) - Container object for all event schemas, not directly used.
* [**events_mapping**](events_mapping.html) - [Mapping result](../../docs/specs/mapping.md) with implicit enumeration
* [**events_validation**](events_validation.html) - Validation device result
* [**monitoring**](monitoring.html) - Output from UDMIS monitoring
* [**persistent_device**](persistent_device.html) - Device persistent data
* [**properties**](properties.html)
* [**query_cloud**](query_cloud.html) - Information specific to how the device communicates with the cloud.
* [**state_mapping**](state_mapping.html) - State for [mapping](../../docs/specs/mapping.md)
* [**state_validation**](state_validation.html) - Validation state summary
