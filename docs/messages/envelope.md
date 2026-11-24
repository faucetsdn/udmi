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

* **`deviceId` vs. `providerId`**: Standard device telemetry and state messages identify their
  origin with `deviceId`. Back-end services emitting registry-scoped messages set `providerId`
  instead and omit `deviceId`. The envelope schema enforces mutual exclusion so an envelope
  cannot set both fields.
* **`subSetKey`**: An optional snake-case identifier matching `^[a-z][a-z0-9]*(_[a-z0-9]+)*$`
  that scopes a logical subset of messages within a `subFolder`. For example, site model
  discovery sets `subFolder` to `discovery` and `subSetKey` to `model` so consumers can
  distinguish model discovery streams from standard network discovery scans.
