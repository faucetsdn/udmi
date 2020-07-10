# UDMI Technology Stack

The complete UDMI specificaiton (super set of the base schema), specifies a complete
technology stack for compliant IoT devices.

# Core Requirements

* [Google Cloud's MQTT Protocol Bridge](https://cloud.google.com/iot/docs/how-tos/mqtt-bridge).
  * This is _not_ the same as a generic MQTT Broker, but it is compatible with standard client-side libraries.
  * Other transports (non-Google MQTT, CoAP, etc...) are acceptable with prior approval.
  * Connected to a specific Cloud IoT Registry designated for each site-specific project.
* Utilizes the MQTT Topic table listed below.
* JSON encoding following the core [UDMI Schema](README.md), specifying the semantic structure of the data.
* Passes the [DAQ Validation Tool](../../docs/validator.md) for all requirements.

# MQTT Topic Table

| Type     | Category | subFolder |                MQTT Topic              |  Schema File  |
|----------|----------|-----------|----------------------------------------|---------------|
| state    | state    | _n/a_     | `/devices/{device_id}/state`           | state.json    |
| config   | config   | _n/a_     | `/devices/{device-id}/config`          | config.json   |
| pointset | event    | pointset  | `/devices/{device-id}/events/pointset` | pointset.json |
| system   | event    | system    | `/devices/{device-id}/events/system`   | system.json   |

# Backend Systems

Any backend system (in a GCP project) should adhere to the following guidelines:
* All messages to/from the devices should conform to the UDMI schema payloads (pass validation).
* All exchanges with the devices should go through a PubSub topic:
  * The _state_ and _event_ messages are published to a topic configured through the IoT Core registry.
  * If necessary, any _config_ or _command_ messages should go through a PubSub topic, and then converted to the requisite Cloud IoT
  config write using a simple cloud function.
* To make data persistent, it can be written to a back-end database, e.g. Firestore. See the `device_telemetry` and
  `device_state` [example cloud functions](../../firebase/functions/index.js) for details.
* A similar function called `device_config` shows how PubSub can be used to update the Cloud IoT configuration.

A config push can be tested with something like:

```
gcloud pubsub topics publish target \
    --attribute subFolder=config,deviceId=AHU-1,projectId=bos-daq-testing,cloudRegion=us-central1,deviceRegistryId=registrar_test \
    --message '{"version": 1, "timestamp": "2019-01-17T14:02:29.364Z"}'
```

The reason for the redirection of any data through a PubSub topic is so that the Cloud IoT registry, if necessary,
can be housed in a different cloud project from the backend applications.
