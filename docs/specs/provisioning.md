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
  participant Devices
  participant Registry
  participant Agent as Agent<br/>(w/ Mapping)
  participant Pipeline
  Devices->>Agent: DISCOVERY EVENT
  Agent->>Registry: (create device)
  Registry->>Agent: (device_num_id)
  Agent->>Pipeline: (config device)
  Devices->>Pipeline: TELEMETRY EVENT
```
