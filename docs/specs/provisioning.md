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
  participant Agent as Agent<br/>(w/ Mapping)
  participant Pipeline
  Devices->>Agent: DISCOVERY EVENT
  Note over Agent: Mapping
  activate Agent
  loop Devices
    Agent->>Registry: (*device_id)
    Registry->>Agent: (*device_num_id)
    Agent->>Pipeline: (*guid, device_id, device_num_id)
  end
  deactivate Agent
  Devices->>Pipeline: TELEMETRY EVENT<br/>(device_id, device_num_id)
```
