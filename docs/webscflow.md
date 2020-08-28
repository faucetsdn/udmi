# Web-based Configuration Flow

Web-based configuraiton flow that applies BACnetSC gateway devices.

## Sequence Diagram

## Source

https://textart.io/sequence#

```
object OnPrem Cloud OffPrem
OffPrem->Cloud: Create GCP Project and Registry
note right of OffPrem: Provision GCP connection in IoT Gateway Device
OffPrem->Cloud: Create enteliCLOUD Site for Device/Object Discovery Tool
note right of OffPrem: Provision URL for BACnet Secure Connect in IoT Gateway Device
OffPrem->OnPrem: Install IoT Gateway Device
OnPrem->Cloud: Connect to GCP
OnPrem->Cloud: Connect to enteliCLOUD
OffPrem->Cloud: Run enteliWEB Discovery Tool
Cloud->OnPrem: BACnet Discovery
OnPrem->Cloud: BACnet Devices, Objects & Properties
Cloud->OffPrem: BACnet Devices, Objects & Properties
note right of OffPrem: Create Cloud Telemetry Metadata Structure
note right of OffPrem: Provision UDMI Registrar for GCP and Metadata
OffPrem->Cloud: Run UDMI Registrar
Cloud->OnPrem: UDMI Gateway & Device Config Blocks
OnPrem->Cloud: Telemetry
```
