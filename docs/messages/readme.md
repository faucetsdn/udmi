[**UDMI**](../../) / [**Docs**](../) / [Messages](#)

# Messages

Messages are organized by _type_ and _folder_, which correspond to the transport aspect of the device
(_type_) and semantic meaning (_folder_).

## Message Folders

Each message has an associated sub block (sometimes called a _subFolder_) that indicates a semantic
scoping of the message or block:

- [pointset](pointset.md) messages deal with data point telemetry (e.g. temperature reading)
- [system](system.md) messages handle system events such as rebooting, config processing, firmware, etc...

## Message Types

Message _types_ are divided into four high-level categories, which define the direction of message
flow, but also the treatment of the message as it moves through the system:

* __Directionality__: Which direction the messages flow with respect to the device
  * __Uplink__: From the device to cloud, used to collect data from/about devices.
  * __Downlink__: From cloud to the device, used to control devices.
* __Treatment__: How the message flows through the system and is stored by the transport layers
  * __Sticky__: Rate-limited, and persist in the system. If multiple messages are sent, only
  the last one is meaningful or maintained (intermediate messages may be proactively dropped by the system).
  There is only one 'sticky' blob of data for each device, shared my all blocks/folders.
  * __Stream__: Passed through the system with minimal on-the-way handling. If multiple messages are sent, all
  of them should make it through although any individual message may be lost in transit. Each 'stream'
  message has its own folder/block associated with it (not shared).

|         | __Uplink__  | __Downlink__  |
|---------|---------|-----------|
| __Sticky__  | _state_ | _config_  |
| __Stream__  | _event_ | _command_ |

The properties and uses of the four types fall out from this accordingly:
* _state_: Sticky to the cloud information from the device, including information such as the overall
status of the device, and any errors or conditions from, e.g., writing points. See
[IoT Core State Docs](https://cloud.google.com/iot/docs/how-tos/config/getting-state) for more information.
* _event_: The canonical streaming telemetry messages from the device, usually containing things
like temperature readings or system memory utilization. See
[IoT Core Event Docs](https://cloud.google.com/iot/docs/how-tos/mqtt-bridge#publishing_telemetry_events) for more information.
* _config_: Ability to control the behavior of a device, e.g. for key rotation, writeback, etc... See
[IoT Core Config Docs](https://cloud.google.com/iot/docs/how-tos/config/configuring-devices) for more information.
* _command_: Direct but transitory messages to a device, e.g. to install a new auth key or perform diagnostic operations.
See [IoT Core Command Docs](https://cloud.google.com/iot/docs/how-tos/commands) for more information.

Individual blocks (folders), such as _system_ or _pointset_ will have their own semantic uses for the various types.
See the individual block folder documentation to learn more about those aspects.

## Message Schemas

- [config](config.md) ([_🧬schema_](../../gencode/docs/config.html))
- [state](state.md) ([_🧬schema_](../../gencode/docs/config.html))
- [event](event.md)
  - [Pointset (telemetry)](pointset.md#telemetry) ([_🧬schema_](../../gencode/docs/event_pointset.html))
  - [System (logging, etc)](system.md) ([_🧬schema_](../../gencode/docs/event_system.html))
  - [Discovery](../specs/discovery.md) ([_🧬schema_](../../gencode/docs/event_discovery.html))
- [envelope](envelope.md)

## MQTT Topics

MQTT topics described in [tech stack](../specs/tech_stack.md)
