# PubSub Setup Documentation

This document describes the [GCP PubSub in Cloud IoT](https://cloud.google.com/iot-core/) mechanism for
processing device messages. There are three major message types employed by the system:
* <b>Config</b>: Messages sent from cloud-to-device that _configure_ the device (idempotent).
* <b>State</b>: Messags sent from device-to-cloud reporting _state_ form the device (idempotent).
* <b>Events</b>: Messages sent from device-to-cloud for streaming _events_ (non-idempotent).

## Message/Schema Mapping

When using the
[GCP Cloud IoT Core MQTT Bridge](https://cloud.google.com/iot/docs/how-tos/mqtt-bridge#publishing_telemetry_events)
there are multiple ways the subschema used during validation is chosen.
* An `events` message is validated against the sub-schema indicated by the MQTT topic `subFolder`. E.g., the MQTT
topic `/devices/{device-id}/events/pointset` will be validated against `.../pointset.json`.
* [Device state messages](https://cloud.google.com/iot/docs/how-tos/config/getting-state#reporting_device_state)
are validated against the `.../state.json` schema.
* All messages have their attributes validated against the `.../attributes.json` schema. These attributes are
automatically defined by the MQTT Client ID and Topic, so are not explicitly included in any message payload.
* The `config` messages are artifically injected into the `target` PubSub topic by the configuration script
(below) so they can be easily checked by the validation engine.

The simple `state_shunt` function in `daq/functions/state_shunt` will automatically send state update messages
to the `target` PubSub topic. Install this function to enable validation of state updates. (Also make sure to
configure the Cloud IoT project to send state message to the state topic!)

