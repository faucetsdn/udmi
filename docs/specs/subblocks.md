[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Subblocks](#)

# Subblocks API

Defined the high-level API between UDMI core services and ancillary applications.
These messages are similar to those used for device communication, but are distinctly
different in some cases (esp. when dealing with state/config).

For each subblock, there's a few basic message exchanges.
* Telemetry (from device): Streaming telemetry. This is essentially a raw feed from the device itself.
* State (from device): Device state for this subblock. Unlike a
  [comprehensive device state message](../../tests/state.tests/example.json)
  this message contains information _only_ for a single subblock.
* Config (for device): Device config for this subblock. Unlike a
  [comprehensive device config message](../../tests/config.tests/example.json)
  this message contains information _only_ for a single subblock.

## Pointset

* [Telemetry](../../tests/event_pointset.tests/example.json)
* [State](../../tests/state_pointset.tests/example.json)
* [Config](../../tests/config_pointset.tests/example.json)

## Discovery

* Telemetry
* State
* Config

## Blobs

* Telemetry
* State
* Config

