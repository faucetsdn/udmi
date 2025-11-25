# A Python Library for UDMI

This project is a high-level, extensible Python library designed to simplify the
development of applications for the **Universal Device Management Interface (
UDMI)**. It provides a clean, Pythonic abstraction over the core UDMI
specification, lowering the barrier to entry for developers and device
manufacturers seeking to create UDMI-compliant IoT solutions.

## Key Features

* **Strict Schema Adherence**: Ensures 1:1 compliance with the UDMI data model
  by using Python dataclasses that are auto-generated directly from the official
  UDMI JSON schemas.

* **Abstracted Complexity**: Handles core device functionalities like MQTT
  client communication, authentication (including certificate management), and
  the primary config/state message loop.

* **Extensible by Design**: Uses abstract base classes to allow for easy
  customization and support for future protocols or features.

The library acts as a reference implementation for demonstrating core
functionalities like connectivity, telemetry, and message handling.

---

## Local Setup & Usage Examples

To install and build the library locally use the build script.

```shell
${UDMI_ROOT}/clientlib/python/bin/build
```

You can find a few samples demonstrating how to connect a device using different
authentication methods as well as other features of the library in
`$UDMI/ROOT/clientlib/python/samples`.
