# Device Gateway

The _gateway_ functionality is used for systems that have legacy, heritage,
or traditional devices that do not communicate directly to the cloud using
a MQTT/UDMI connection. For example, an older [BACnet](http://www.bacnet.org/)
based system could use a gateway to translate on-prem communications into UDMI.

The
[Google Cloud IoT Core Gateway Documentation](https://cloud.google.com/iot/docs/how-tos/gateways)
provides an overview of the cloud-side implementation of a gateway. UDMI, then,
specifies an additional layer of specification around the associated
message formats.

Conceptually, there are two types of
entities involved: the _gateway device_, and the _proxied device_. Both of
these are 'devices' in the sense that they have an entry in a cloud registry
and have device-level UDMI data, but they have fundamentally different roles.

## Gateway Operation

There are two modes for gateway operation: _implicit_ and _explicit_. In the
_explicit_ mode, the gateway functionality is configured dynamically through
gateway _config_ messages, which tell it the local devices it should proxy
for. In a _implicit_ gateway configuration, the gateway will be directly
configured to proxy a set of devices, essentially ignoring any gateway
information in the associated _config_ block.

The general sequence of events for gateway operation is:
1. Optional metadata specifies configuration paramaters that should be used
at install time to properly (manually) setup the device.
2. (_explicit_ only) On startup, the gateway connects to the cloud and receives
a configuration block that details which _proxy devices_ the gateway should proxy for.
4. Gateway 'attaches' (Cloud IoT terminology) to the proxied devices,
receiving a configuration block for each proxied device. Any attach errors are
indicated in the gateway _status_ block and sent along as a _logentry_ event.
5. (_explicit_ only) The proxied device's _config_ block specifies any local
connection parameters for the proxied device, e.g. the BacNET device id.
6. The gateway proxies communication to/from the device, translating between
native (e.g. BacNET) communications and UDMI-based messages.

### config

The [gateway config block](../tests/config.tests/gateway.json)
simply specifies the list of target proxy devices.
On a config update, the gateway is responsible for handling any change in
this list (added or removed devices). The details of proxied devices are
kept to a minimum here (IDs only) to avoid overflowing the allowed block
size in cases where there are a large number of devices.

### state

Any attach errors, e.g. the gateway can not successfully attach to the target
device, should be reported in the [gateway state](../tests/state.tests/gateway.json)
and a _logentry_ message used to detail the
nature of the problem. If the gateway can attach successfully, any other
errors, e.g. the inability to communicate with the device over the local
network, should be indicated as part of the proxy device status block.

### telemetry

Telemety from the gateway would primarily consist of standard
[_system_](../tests/event.tests/example.json) messages, which
provide a running comentary about gateway operation. Specificaly, if there
is an error attaching, then there should be appropriate logging to help
diagnose the problem.

### metadata

The gateway [metadata block](../tests/metadata.tests/gateway.json) specifies
any information necessary either for the
initial (manual) configuration of the device or ongoing validation of
operation. E.g., if a gateway device has a unique MAC address used for
local communications, it would be indicated here.

## Proxy Device Operation

Proxy devices are those that have a logical cloud device entry (in a registry),
and are associated (bound) to a particular gateway. On-prem, the device
itself talks a local protocol (e.g. BacNET), but does not have a direct
cloud connection.

### config

[Proxy device config blocks](../tests/config.tests/proxy.json) contain a special
_localnet_ section that
specifies information required by the gateway to contact the local device.
E.g., the fact that a device is 'BacNET' and also the device's BacNET object
ID. Based on this, the gateway can communicate with the target device and proxy
all other messages.

Additionally, the gateway is responsible for proxying all other supported
operations of the config bundle. E.g., if a _pointset_ "set_value" parameter
is specified, the gateway would need to convert that into the local protocol
and trigger the required functionality.

### state

There is no gateway-specific _state_ information, but similarly to _config_ the
gateway is responsible for proxying all relevant state from the local device
into the proxied device's state block. E.g., if the device is in an alarm
state, then the gateway would have to transform that from the local format
into the appropriate UDMI message.

### telemetry

Telemetry is handled similarly, with the gateway responsible for proxying data
from local devices through to UDMI. In many cases, this would be translating
specific device points into a [_pointset_ message](../tests/event_pointset.tests/example.json).

### metadata

A [proxy device metadata section](../tests/metadata.tests/proxy.json) describes
_localnet_ with the presence of the
device on a local network. This can/should be used for initial programming
and configuration of the device, or to validate proper device configuration.
The gateway implementation itself would not directly deal with this block.
