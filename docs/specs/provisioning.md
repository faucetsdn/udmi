[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Provisioning](#)

# Provisioning

Provisioning is the process of setting up various parts of the system to make them
functional in a given integration: for example, assigning authentication keys to
an IoT device.

Part of the overall [onboarding](onboarding.md) process.

## Sequence Diagram

```mermaid
sequenceDiagram
  %%{wrap}%%
  participant Devices as Devices<br/>(w/ Spotter)
  participant Registry
  participant Provisioning Engine
  participant Pipeline
  Devices->>Provisioning Engine: DISCOVERY EVENT
  Note over Provisioning Engine: Mapping
  activate Provisioning Engine
  loop Devices
    Provisioning Engine->>Registry: (*device_id)
    Registry->>Provisioning Engine: (*device_num_id)
    Provisioning Engine->>Pipeline: (*guid, device_id, device_num_id)
  end
  deactivate Provisioning Engine
  Devices->>Pipeline: TELEMETRY EVENT<br/>(device_id, device_num_id)
```
