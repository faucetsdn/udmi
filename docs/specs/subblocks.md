[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Subblocks](#)

# Subblocks API

The _Subblocks API_ defines a high-level interface between UDMI core services and ancillary
applications. These messages are similar to those used for device communication, but are
Specifically segmented by designated _subblocks_ that partition functionality into atomic
chunks. Specficialy, the subblock _state_/_config_ are a limited form of the overall
device _state_/_config_, and only expose the relevant pieces.

The basic mode of this interface is a "read only" subscription to a PubSub topic (normally
`udmi_target`) that then provides a complete view of updates as they flow through the system.
For example, a cloud-to-device _config_ update would be published on this topic as a "update
to device config." This level of visability should be sufficient to completely mirror the
visible state of the system (barring issues like loss-of-message etc...).

The various _subblocks_ are detailed below. Each _subblock_ (or _subFolder_ if you're looking
at the PubSub _message envelope_), has several basic _subTypes_ that manifest themselves from
different sources:

* **Model**: Model-based description of this device. Unlike the other messages, this exists
  independent of any actual physical device, and will be injected by the system through something
  like the `registrar` tool. The _model_ is typically also refleced in a _site\_model_ as a
  static set of files somewhere.
* **Event**: Streaming telemetry. This is essentially a raw feed from the device itself,
  and will always be segmented by subblock (e.g. for a
  [pointset event](../../tests/event_pointset.tests/example.json)). Streaming telemetry
  is sent from the device, so will be _received by_ a client app.
* **State**: Device state for this subblock. Unlike a
  [comprehensive device state message](../../tests/state.tests/example.json)
  this message contains information _only_ for a single subblock. Used for reporting any 'sticky'
  state from a device, so will be _received by_ a client app.
* **Config**: Device config for this subblock. Unlike a
  [comprehensive device config message](../../tests/config.tests/example.json)
  this message contains information _only_ for a single subblock. Used for writing configuration
  changes to a device, so will be _sent from_ a client app.
* **Commands**: Streaming commands from cloud to the device. Generally not used because a model-based
  approach using device state is preferred.

In all cases, the _Subblock API_ messages encode the relevant subblock ID { pointset, discovery, ... }
in the [message envelope's](../../tests/envelope.tests/example.json) _subFolder_ field.
The _subType_ field encodes the relevant type { _event_, _config_, _state_, _model_ }.

## System

* **Model**
* **Event**
* **State**
* **Config**

## [Pointset](../messages/pointset.md)

A _pointset_ covers the structures necessary to model a device _point_ with associated data readings.
This is typically what is thought of as 'device telemetry' and represents the expected end value of
the device-to-cloud connection.

* [**Model**](../../tests/model_pointset.tests/example.json): Expected model for what the device should
  be reporting.
* [**Event**](../../tests/event_pointset.tests/example.json): Streaming telemetry events from the device
  containing the actual data points.
* [**State**](../../tests/state_pointset.tests/example.json): State of the device points, indicating any
  sticky errors or status of any cloud-to-device config.
* [**Config**](../../tests/config_pointset.tests/example.json): Configuration of the device points,
  indicating the expected points, cloud-to-device control, etc...

## Discovery

_Discovery_ covers systems that are actively searching for systems on a network (of some kind), and
returning results about what was discovered and what their capabilities are. This provides a mechanism
for doing things like a BACnet discovery sequence.

* **Model**
* **Event**
* **State**
* **Config**

## Audit

_Audit_ covers an external "black box" audit of a system for vulnerabilities or other characteristics.
E.g., doing a port-scan of a device to see what network ports are open would be part of a network
exposure audit.

* **Model**
* **Event**
* **State**
* **Config**

## Mapping

The _mapping_ process covers the determination of a translation from a set of identifiers or points to
a canonical or other set of identifiers or points. E.g., there's a mapping process that goes on to
correlate a BACnet MAC address (such as `9832C2`) with an associated IoT ID (such as `FCU-323`).

* **Model**
* **Event**
* **State**
* **Config**

