[**UDMI**](../) / [Docs](#)

# UDMI specification and tools

## Contents

- [**Specifications**](specs/)
- [**Messages**](messages/)
- [**Guides**](guides/)
- [**Learning**](learning/)
- [**Cloud Platforms**](cloud/)
- [**Tools**](tools/)
- [**Schema**](https://github.com/faucetsdn/udmi/tree/master/schema)
  ([_🧬Interactive viewer_](../gencode/docs/))
- [**Device Testing Results**](device_testing/)


## About UDMI

The Universal Device Management Interface (UDMI) provides a high-level specification for the
management and operation of physical IoT systems. This data is typically exchanged
with a cloud entity that can maintain a "digital twin" or "shadow device" in the cloud. Please
join the [udmi-discuss@googlegroups.com](https://groups.google.com/forum/#!forum/udmi-discuss)
mailing list for questions and discussion.

By design, this schema is intended to be:
- **U**niversal Apply to all subsystems in a building, not a singular vertical solution.
- **D**evice: Operations on an IoT _device_, a managed entity in physical space.
- **M**anagement: Focus on device _management_, rather than command & control.
- **I**nterface: Define an interface specification, rather than a client-library or
RPC mechanism.

The following documents give a high level overview of UDMI:
- [Tech Primer](tech_primer.md)
- [Technology stack](specs/tech_stack.md)
- [UDMI compliance](specs/compliance.md)
- [Validation Workflow](guides/workflow.md)
- [UDMIS architecture](udmis/readme.md)

## System Interfaces

UDMI functions within three primary interfaces to manage devices, registries, and applications:
*   **[Devices (UDMI proper)](specs/compliance.md):** Edge-side device-to-system interface used by physical or simulated on-premise hardware to report telemetry streams (`events`) and dynamically receive or acknowledge operational `config`.
*   **[Internal Tools (Reflector)](specs/tech_stack.md):** Administrative database-to-system interface used by backend registration and synchronization tooling (such as `registrar`) to manage site models, provision cryptographic keys, and reconcile device records.
*   **[Applications (UUFI)](specs/uufi.md):** App-to-system messaging interface used by external applications, dashboards, and operators to query system state, update model specifications, and command devices.

## Use Cases

The essence behind UDMI is an automated mechanism for IoT system management. Many current
systems require direct-to-device access, such as through a web browser or telnet/ssh session.
These techniques do not scale to robust managed ecosystems since they rely too heavily on
manual operation (aren't automated), and increase the security exposure of the system
(since they need to expose these management ports).

UDMI is intended to support a few primary use-cases:
- [_Telemetry Ingestion_](messages/pointset.md#telemetry): Ingest device data points in a standardized format.
- [_Gateway Proxy_](specs/gateway.md): Proxy data/connection for non-UDMI devices,
allowing adaptation to legacy systems.
- [_On-Prem Actuation_](specs/sequences/writeback.md): Ability to effect on-prem device behavior.
- _Device Testability_: e.g. Trigger a fake alarm to test reporting mechanisms.
- [_Commissioning Tools_](tools/): Streamline complete system setup and install.
- _Operational Diagnostics_: Make it easy for system operators to diagnose basic faults.
- [_Status and Logging_](messages/status.md): Report system operational metrics to hosting infrastructure.
- _Key Rotation_: Manage encryption keys and certificates in accordance with best practice.
- _Credential Exchange_: Bootstrap higher-layer authentication to restricted resources.
- _Firmware Updates_: Initiate, monitor, and track firmware updates across an entire fleet
of devices.
- [_On-Prem Discovery_](specs/discovery.md): Enumerate any on-prem devices to aid setup or anomaly detection.
