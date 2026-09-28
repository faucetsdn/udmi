[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Provisioning](#)

# Provisioning

Provisioning is the third phase in the overall [Onboarding](onboarding.md) flow, following [Discovery](discovery.md) and [Mapping](mapping.md).

Provisioning is the process of setting up various parts of the system to make them
functional in a given integration: for example, assigning authentication keys to
an IoT device.

## Sequence Diagram

```mermaid
sequenceDiagram
  %%{wrap}%%
  participant Devices
  participant Mapping
  participant Butler as Butler<br/>(Registrar)
  participant Source Repo
  participant Pipeline
  Mapping->>Butler: Proposed Model
  Note over Butler: Approval
  Butler->>Source Repo: Fetch Model
  Butler->>Pipeline: Pipeline Config
  Devices-->>Pipeline: Pointset (Telemetry)
```
