# UDMI Schema 

The Universal Device Management Interface (UDMI) provides a high-level specification for the
management and operation of physical IoT systems. This data is typically exchanged
with a cloud entity that can maintain a "digital twin" or "shadow device" in the cloud. Please
join the [udmi-discuss@googlegroups.com](https://groups.google.com/forum/#!forum/udmi-discuss)
mailing list for questions and discussion.

By design, this schema is intended to be:
* <b>U</b>niversal: Apply to all subsystems in a building, not a singular vertical solution.
* <b>D</b>evice: Operations on an IoT _device_, a managed entity in physical space.
* <b>M</b>anagement: Focus on device _management_, rather than command & control.
* <b>I</b>nterface: Define an interface specification, rather than a client-library or
RPC mechanism.

See the associated [UDMI Tech Stack](docs/tech_stack.md) for details about transport mechanism
outside of the core schema definition. Nominally meant for use with
[Googe's Cloud IoT Core](https://cloud.google.com/iot/docs/), it can be applied to any set
of data or hosting setup.

## Recommended Workflow

The [recommended workflow](docs/workflow.md) for UDMI covers using the _registrar_ and
_validator_ tools to configure and test a cloud project. Additionally, the _pubber_ tool
is instrumental in setting up and testing the system independent of actual device setup.

## Use Cases

The essence behind UDMI is an automated mechanism for IoT system management. Many current
systems require direct-to-device access, such as through a web browser or telnet/ssh session.
These techniques do not scale to robust managed ecosystems since they rely too heavily on
manual operation (aren't automated), and increase the security exposure of the system
(since they need to expose these management ports).

UDMI is intended to support a few primary use-cases:
* _Telemetry Ingestion_: Ingest device data points in a standardized format.
* [_Gateway Proxy_](docs/gateway.md): Proxy data/connection for non-UDMI devices,
allowing adaptation to legacy systems.
* _On-Prem Actuation_: Ability to effect on-prem device behavior.
* _Device Testability_: e.g. Trigger a fake alarm to test reporting mechanisms.
* _Commissioning Tools_: Streamline complete system setup and install.
* _Operational Diagnostics_: Make it easy for system operators to diagnose basic faults.
* _Status and Logging_: Report system operational metrics to hosting infrastructure.
* _Key Rotation_: Manage encryption keys and certificates in accordance with best practice.
* _Credential Exchange_: Bootstrap higher-layer authentication to restricted resources.
* _Firmware Updates_: Initiate, monitor, and track firmware updates across an entire fleet
of devices.
* _On-Prem Discovery_: Enumerate any on-prem devices to aid setup or anomaly detection.

All these situations are conceptually about _management_ of devices, which is conceptually
different than the _control_ or _operation_. These concepts are similar to the _management_,
_control_, and _data_ planes of
[Software Defined Networks](https://queue.acm.org/detail.cfm?id=2560327).
Once operational, the system should be able to operate completely autonomoulsy from the
management capabilities, which are only required to diagnose or tweak system behavior.

## Design Philosophy

In order to provide for management automation, UDMI strives for the following principles:
* <b>Secure and Authenticated:</b> Requires a properly secure and authenticated channel
from the device to managing infrastructure.
* <b>Declarative Specification:</b> The schema describes the _desired_ state of the system,
relying on the underlying mechanisms to match actual state with desired state. This is
conceptually similar to Kubernetes-style configuration files.
* <b>Minimal Elegant Design:</b> Initially underspecified, with an eye towards making it easy to
add new capabilities in the future. <em>It is easier to add something than it is to remove it.</em>
* <b>Reduced Choices:</b> In the long run, choice leads to more work
to implement, and more ambiguity. Strive towards having only _one_ way of doing each thing.
* <b>Structure and Clarity:</b> This is not a "compressed" format and not designed for
very large structures or high-bandwidth streams.
* <b>Property Names:</b>Uses <em>snake_case</em> convention for property names.
* <b>Resource Names:</b> Overall structure (when flattened to paths), follows the
[API Resource Names guidline](https://cloud.google.com/apis/design/resource_names).

## Subsystem Blocks

UDMI provides a means to multiplex multiple functional subsystems through the same shared
communication channel. There are a number of subsystems that make up the core UDMI spec:

* Core [_system_](docs/system.md) messages about the base device itself.
* Device [_pointset_](docs/pointset.md) for device telemetry organized by points.
* Optional [_gateway_](docs/gateway.md) functionality for proxying device/MQTT connections.
* Local [_discover_](docs/discover.md) for discovering device and network capabilities.

## Schema Structure

Schemas are broken down into several top-level sub-schemas that are invoked for
different aspects of device management:
* Device _metadata_ ([example](tests/metadata.tests/example.json)) stored in the cloud about a device,
but not directly available to or on the device, defined by [<em>metadata.json</em>](schema/metadata.json).
This is essentially a specification about how the device should be configured or
expectations about what the device should be doing.
* Message _envelope_ ([example](tests/envelope.tests/example.json)) for server-side
attributes of received messages, defined by [<em>envelope.json</em>](schema/envelope.json). This is
automatically generated by the transport layer and is then available for server-side
processing.
* Streaming device telemetry, which can take on several different forms, depending on the intended
use, e.g.:
* Device _state_ ([example](tests/state.tests/example.json)), sent from device to cloud,
defined by [<em>state.json</em>](schema/state.json). There is one current _state_ per device,
which is considered sticky until a new state message is sent.
is comprised of several subsections (e.g. _system_ or _pointset_) that describe the
relevant sub-state components.
* Device _config_ ([example](tests/config.tests/example.json)), passed from cloud to device,
defined by [<em>config.json</em>](schema/config.json). There is one active _config_ per device,
which is considered current until a new config is received.

A device client implementation will typically only be aware of the _state_, _config_, and
one or more _telemetry_ messages (e.g. _pointset_), while all others are meant for the supporting
infrastructure.

### Metadata Registration and Validation

Using UDMI on a project entails not only the base device and server implementations, but also
properly registering and validating device configuration. The [registrar](docs/registrar.md)
tool and [validator](docs/validator.md) tool provide a means to configure and check site
installations, respectively.

### State Message

* See notes below about 'State status' fields.
* There is an implicit minimum update interval of _one second_ applied to state updates, and it
is considered an error to update device state more often than that. If there are multiple
_state_ updates from a device in under a second they should be coalessed into one update
(sent after an appropriate backoff timer) and not buffered (sending multiple messages).
* `last_config` should be the timestamp _from_ the `timestamp` field of the last successfully
parsed `config` message (not the timestamp the message was received/processed).

### Config Message

* `sample_rate_sec`: Sampling rate for the system, which should proactively send an
update (e.g. _pointset_, _logentry_, _discover_ message) at this interval.
* `sample_limit_sec`: Minimum time between sample updates. Updates that happen faster than this time
(e.g. due to _cov_ events) should be coalesced so that only the most recent update is sent.
* `set_value`: Set a value to be used during diagnostics and operational use. Should
override any operational values, but not override alarm conditions.
* `min_loglevel`: Indicates the minimum loglevel for reporting log messages below which log entries
should not be sent. See note below for a description of the level value.
