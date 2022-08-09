[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Onboarding](#)

# Onboarding

The overall "onboarding" flow consists of a number of separate subflows stitched together for a complete
end-to-end process to take an "unknown" device and ensure that it's properly integrated with backend services.

At a high-level, the process involves different message subgroups that handle slightly different
scopes of device data:
* **(Native)**: Device communicaiton using some non-UDMI native protocol (e.g. BACnet, Modbus, etc...)
* **[Discovery](discovery.md)**: Messages relating to the discovery (and provisioning) of devices (e.g. messy BACnet info)
* **[Mapping](mapping.md)**: Messages relating to a 'resolved' device type and ID (e.g. the device is an `AHU` called `AHU-1`)
* **[Pointset](../messages/pointset.md)**: Messages relating to actual data flow (e.g. temperature reading), essentially the interesting stuff
* **(Onboard)**: Interactions with an external (non-UDMI) entity of some kind to facilitate onboarding of devices

## Sequence Diagram

The overall onboarding sequence involves multiple components that work together to provide the overall flow:
* **Devices**: The target things that need to be discovered, configured, and ultimately communicate point data.
* **Spotter**: A on-prem node responsible for handling discovery scans of devices.
* **Agent**: Agent responsible for managing the overall _discovery_ and _mapping_ process (how often, what color, etc...).
* **Engine**: Mapping engine that uses hueristics, ML, or a UI to convert discovery information into a concrete device/sink mapping.
* **Sink**: Ultimate recepient of pointset information, The thing that cares about 'temperature' in a room.

Notes & Caveats:
1. Only "interesting" messages are shown in the diagram, there's other control flow things that go on (e.g.
to configure when the *Spotter* should activate) to complete the overall flow.
2. This shows the flow for a direct-connect (no IoT Gateway involved) device. The overall flow for a proxied device
(with IoT Gateway) would more or less be the same with some additional intermediaries.

```
+---------+    +---------+             +-------+            +---------+ +-------+
| Devices |    | Spotter |             | Agent |            | Engine  | | Sink  |
+---------+    +---------+             +-------+            +---------+ +-------+
     |              |                      |                     |          |
     |              |     Discovery Config |                     |          |
     |              |<---------------------|                     |          |
     |              |                      |                     |          |
     |       Probes |                      |                     |          |
     |<-------------|                      |                     |          |
     |              |                      |                     |          |
     | Replies      |                      |                     |          |
     |------------->|                      |                     |          |
     |              |                      |                     |          |
     |              | Discovery Events     |                     |          |
     |              |------------------------------------------->|          |
     |              |                      |                     |          |
     |              |                      | Mapping Config      |          |
     |              |                      |-------------------->|          |
     |              |                      |                     |          |
     |              |                      |      Mapping Events |          |
     |              |                      |<--------------------|          |
     |              |                      |                     |          |
     |              |                      | Onboard Requests    |          |
     |              |                      |------------------------------->|
     |              |                      |                     |          |
     | Telemetry Events                    |                     |          |
     |--------------------------------------------------------------------->|
     |              |                      |                     |          |
```

1. **[Discovery Config](../../tests/config.tests/discovery.json)** indicates that the _spotter_ should do the needful and scan the local network.
1. **(Probes)** scan for natively-encoded _device_ information from devices (format out of scope for UDMI)
2. **(Deplies)** contain the discovered natively-encoded _device_ information, e.g.:
  * "I am device `78F936`, with points { `room_temp`, `step_size`, and `operation_count` }, and public key `XYZZYZ`"
2. **[Discovery Events](../../tests/event_discovery.tests/enumeration.json)** wraps the device info from the _spotter_ into a UDMI-normalized format, e.g.:
  * "Device `78F936` has points { }, with a public key `XYZZYZ`"
3. **[Mapping Config](../../tests/config_mapping.tests/mapping.json)** from the _agent_ indicates that the _engine_ should export responses.
3. **[Mapping Events](../../tests/event_mapping.tests/mapping.json)** from the _engine_ contain actual calculated point mappings.
  * "Device `78F936` is an `AHU` called `AHU-183`, and `room_temp` is really a `flow_temperatue`"
4. **Onboard Requests** are requests to the appropriate _sink_ to onboard a device (contents are defined by _sink_ and out of scope for UDMI)
8. **[Telemetry Events](../../tests/event_pointset.tests/example.json)** are data events from _device_ to _sink_... business as usual!
  * "I am `AHU-183`, and my `room_temp` is `73`"

## Source
Created using https://textart.io/sequence#
```
object Devices Spotter Agent Engine Sink
Agent->Spotter: Discovery Config
Spotter->Devices: Probes
Devices->Spotter: Replies
Spotter->Engine: Discovery Events
Agent->Engine: Mapping Config
Engine->Agent: Mapping Events
Agent->Sink: Onboard Requests
Devices->Sink: Telemetry Events
```
