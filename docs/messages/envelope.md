[**UDMI**](../../) / [**Docs**](../) / [**Messages**](./) / [Envelope](#)

# UDMI Envelope

**Schema Definition:** [config.json](../../schema/envelope.json)

The UDMI `envelope` is not a message itself, per se, but the attributes and other information that
is delivered along with a message. There is no direct awareness of it at the device level: it is
added and manipulated in the back-end only. Primarily based on the
[Cloud IoT Core message attributes](https://cloud.google.com/iot/docs/how-tos/mqtt-bridge#publishing_telemetry_events).

From the device side, the `subFolder` property of the MQTT topic is passed along to the message
envelope, which allows the back-end to properly categorize and process data. A device does not need
to do anything other than specify the correct `subFolder` postfix of the MQTT topic.
