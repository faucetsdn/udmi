[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Tech Stack](#)

# UDMI Technology Stack

The complete UDMI specification (super set of the base schema), specifies a complete
technology stack for compliant IoT devices.

# Core Requirements

* MQTT broker or bridge
  * Available with a local setup using the standard `mosquitto`
  * Cloud-based solutions such as [ClearBlade IoT Core](https://www.clearblade.com/iot-core/)
  * Anything else that works... (it's OSS so go crazy!)
* Utilizes the MQTT Topic table listed below (can be customized)
* JSON encoding following the core schema definition, specifying the semantic structure of the data.

# MQTT Topic Suffix Table

| Type     | Category | subFolder | MQTT Topic Suffix                | Schema File   |
| -------- | -------- | --------- | -------------------------------- | ------------- |
| state    | state    | _n/a_     | `{topic_prefix}/state`           | state.json    |
| config   | config   | _n/a_     | `{topic_prefix}/config`          | config.json   |
| pointset | event    | pointset  | `{topic_prefix}/events/pointset` | pointset.json |
| system   | event    | system    | `{topic_prefix}/events/system`   | system.json   |

For many implementations the full topic would be `/devices/{device_id}/{suffix}`

# Backend Systems

Any backend system should adhere to the following guidelines:
* All messages to/from the devices should conform to the UDMI schema payloads (pass validation).
* All exchanges with the devices should go through a PubSub topic:
  * The _state_ and _events_ messages are published to a topic configured through the IoT Core registry.
  * If necessary, any _config_ or _command_ messages should go through a PubSub topic, and then converted to the requisite Cloud IoT
  config write using a simple cloud function.

A config push can be tested with something like:

```
gcloud pubsub topics publish target \
    --attribute subFolder=config,deviceId=AHU-1,projectId=bos-daq-testing,cloudRegion=us-central1,deviceRegistryId=registrar_test \
    --message '{"version": 1, "timestamp": "2019-01-17T14:02:29.364Z"}'
```

The reason for the redirection of any data through a PubSub topic is so that the Cloud IoT registry, if necessary,
can be housed in a different cloud project from the backend applications.
