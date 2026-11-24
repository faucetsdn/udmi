[**UDMI**](../../) / [**Docs**](../) / [**Messages**](./) / [Envelope](#)

# UDMI Envelope

**Schema Definition:** [config.json](../../schema/envelope.json)

The UDMI `envelope` is not a message itself, but the attributes and other information that
is delivered along with a message. There is no direct awareness of it at the device level: it is
added and manipulated in the back-end only. Primarily based on the message attributes.

From the device side, the `subFolder` property of the MQTT topic is passed along to the message
envelope, which allows the back-end to properly categorize and process data. A device does not need
to do anything other than specify the correct `subFolder` postfix of the MQTT topic.

## Provider Messages and Subset Keys

* **`deviceId` and `providerId` (mutually exclusive)**: Standard device telemetry and state
  messages identify their origin with `deviceId`. Back-end services that emit registry-scoped
  messages on behalf of a service rather than an individual device set `providerId` instead and
  omit `deviceId`. A message envelope must not set both `deviceId` and `providerId` (enforced by
  the envelope schema).
* **`subSetKey`**: An optional snake-case identifier (`^[a-z][a-z0-9]*(_[a-z0-9]+)*$`) that scopes
  a logical subset of messages within a `subFolder`. For example, site model discovery sets
  `subFolder` to `discovery` and `subSetKey` to `model` so consumers can distinguish model
  discovery streams from standard network discovery scans.
