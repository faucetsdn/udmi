[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Subblocks](#)

# Subblocks API

The _Subblocks API_ defines the high-level API between UDMI core services and ancillary
applications. These messages are similar to those used for device communication, but are
Specifically segmented by designated _subblocks_ that partition functionality into atomic
chunks. Specficialy, the subblock _state_/_config_ are a limited form of the overall
device _state_/_config_, and only expose the relevant pieces.

For each subblock, there's a few typical message exchanges:
* **Telemetry**: Streaming telemetry. This is essentially a raw feed from the device itself,
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

In all cases, the _Subblock API_ messages encode the relevant subblock ID { pointset, discovery, ... }
in the [message envelope's](../../tests/envelope.tests/example.json) _subFolder_ field.
The _subType_ field encodes the relevant { event, config, state }.

## [Pointset](../messages/pointset.md)

* [**Telemetry**](../../tests/event_pointset.tests/example.json)
* [**State**](../../tests/state_pointset.tests/example.json)
* [**Config**](../../tests/config_pointset.tests/example.json)

## System

* **Telemetry**
* **State**
* **Config**

## Discovery

* **Telemetry**
* **State**
* **Config**

## Blobs

* **Telemetry**
* **State**
* **Config**

