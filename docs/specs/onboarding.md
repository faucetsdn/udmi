[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Onboarding](#)

# Onboarding

The overall "onboarding" flow consists of a number of separate subflows stitched together for a complete
end-to-end process. This generall starts from an "unknown" device in the system through to a UDMI-compliant
device that's properly integrated with backend services.

At a high-level, the overall process involves different message subgroups that handle slightly different
scopes of device data:
* **(Native)**: Device communicaiton using some non-UDMI native protocol (e.g. BACnet, Modbus, etc...)
* **Discovery**: Messages relating to the discovery (and provisioning) of devices (e.g. messy BACnet info)
* **Mapping**: Messages relating to a 'resolved' device type and ID (e.g. the device is an `AHU` called `AHU-1`)
* **Pointset**: Messages relating to actual data flow (e.g. temperature reading), essentially the interesting stuff

## Sequence Diagram

The overall onboarding sequence involves multiple components that work together to provide the overall flow:
* **Device**: The target thing that needs to be discovered, configured, and ultimately communications point data
* **Node**: A 'discovery node' responsible for handling on-prem non-UDMI discovery communication with a device
* **Cloud**: The on-prem/in-cloud boundary. Things to the left are things in the building, to the right are in the cloud
* **Agent**: Responsible for managing the overall _discovery_ process (how often, what color, etc...)
* **Mapper**: Uses hueristics, ML, or a UI to convert discovery information into a concrete device/sink mapping
* **Sink**: Ultimate recepient of pointset information. The thing that cares about 'temperature' in a room

Notes & Caveats:
1. Only "interesting" messages are shown in the diagram, there's other control flow things that go on (e.g.
to configure when the discovery *Node* should activate) to complete the overall flow.
2. This just shows one-of-many potential provisioning (handling keys) techniques. There's other paths
that would be possible (including manually, which is the baseline default).
3. This shows the flow for a direct-connect (no IoT Gateway) device. The overall flow for a proxied device
(with IoT Gateway) would more or less be the same, just different details about exact communication mechanisms.

```
+---------+               +-------+ +-------+               +-------+           +--------+          +-------+
| Device  |               | Node  | | Cloud |               | Agent |           | Mapper |          | Sink  |
+---------+               +-------+ +-------+               +-------+           +--------+          +-------+
     |                        |         |                       |                    |                   |
     | (Info)                 |         |                       |                    |                   |
     |----------------------->|         |                       |                    |                   |
     |                        |         |                       |                    |                   |
     |                        | Discovery Event                 |                    |                   |
     |                        |----------------------------------------------------->|                   |
     |                        |         |                       |                    |                   |
     |                        |         |                       |                    | Mapping Event     |
     |                        |         |                       |                    |------------------>|
     |                        |         |                       |                    |                   |
     |                        |         |                       |      Mapping Event |                   |
     |                        |         |                       |<-------------------|                   |
     |                        |         |                       |                    |                   |
     |                        |         |     (Cloud Provision) |                    |                   |
     |                        |         |<----------------------|                    |                   |
     |                        |         |                       |                    |                   |
     |                        |         |     Discovery Command |                    |                   |
     |                        |<--------------------------------|                    |                   |
     |                        |         |                       |                    |                   |
     |     (Device Provision) |         |                       |                    |                   |
     |<-----------------------|         |                       |                    |                   |
     |                        |         |                       |                    |                   |
     |                        |         |                       |                    |   Pointset Config |
     |<--------------------------------------------------------------------------------------------------|
     |                        |         |                       |                    |                   |
     | Pointset Event         |         |                       |                    |                   |
     |-------------------------------------------------------------------------------------------------->|
     |                        |         |                       |                    |                   |
```

1. **(Info)** contains natively-encoded _Device_ information (format out of scope for UDMI)
  * "I am device `78F936`, with points { `room_temp`, `step_size`, and `operation_count` }, and public key `XYZZYZ`"
2. **[Discovery Event](../../tests/event_discovery.tests/enumeration.json)** from _Node_ wraps up the device info into a UDMI-normalized format
  * "Device `78F936` has points { }, with a public key `XYZZYZ`"
3. **Mapping Event** from the _Mapper_ (recieved by both _Sink_ and _Agent_)
  * "Device `78F936` is an `AHU` called `AHU-183`, and `room_temp` is really a `flow_temperatue`"
5. **(Cloud Provision)** from _Agent_ sets up the _Cloud_ layer using the IoT Core API.
  * "Device `AHU-183` exists and has public key `XYZZYZ`"
4. **Discovery Command** from the _Agent_ to the discovery _Node_ contains information necessary to provision the device
  * "Device `78F936` should call itself `AHU-183` when connecting to the cloud"
5. **(Device Provision)** uses some natively-encoded mechanism for setting up the device with relevant cloud info
  * "Device `78F936`, you are celled `AHU-183` when connecting to the cloud"
7. **Pointset Config** from the _Sink_ can go directly to the _Device_ (after it connects to the cloud)
  * "Device `AHU-183`, you should send the `room_temp` data point every `10 minutes`"
8. **Pointset Events** sends telemetry events from the _Device_ to _Sink_... business as usual!
  * "I am `AHU-183`, and my `room_temp` is `73`"

## Source
Created using https://textart.io/sequence#
```
object Device Node Cloud Agent Mapper Sink
Device->Node: (Info)
Node->Mapper: Discovery Event
Mapper->Sink: Mapping Event
Mapper->Agent: Mapping Event
Agent->Cloud: (Cloud Provision)
Agent->Node: Discovery Command
Node->Device: (Device Provision)
Sink->Device: Pointset Config
Device->Sink: Pointset Event
```
