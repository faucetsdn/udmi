[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Onboarding](#)

# Onboarding

The overall 'onboarding' flow consists of a number of separate subflows stitched together for a complete
end-to-end process. This generall starts from an "unknown" device in the system, ultimately resulting in
a UDMI-complient setup.

Message Subgroups:
* (Native): Device communicaiton using some non-UDMI native protocol (e.g. BACnet, Modbus, etc...)
* Discovery: Messages relating to the discovery (and provisioning) of devices (e.g. messy BACnet info)
* Mapping: Messages relating to a 'resolved' device type and ID (e.g. the device is an `AHU` called `AHU-1`)
* Pointset: Messages relating to actual data flow (e.g. temperature reading), essentially business as usual

## Sequence Diagram

Functional Components:
* Device: The target thing that needs to be discovered, configured, and ultimately communications point data
* Node: A 'discovery node' responsible for handling on-prem non-UDMI discovery communication with a device
* Cloud: The on-prem/in-cloud boundary. Things to the left are things in the building, to the right are in the cloud
* Agent: Responsible for managing the overall _discovery_ process (how often, what color, etc...)
* Matcher: Uses hueristics, ML, or a UI to convert discovery information into a concrete device/sink mapping
* Sink: Ultimate recepient of pointset information. The thing that cares about 'temperature' in a room

Note: Only "interesting" messages are shown in the diagram, there's other 'control flow' things that go on (e.g.
to configure when the discovery *Node* should activate) to complete the overall flow.
```
+---------+        +-------+ +-------+ +-------+    +---------+    +-------+
| Device  |        | Node  | | Cloud | | Agent |    | Matcher |    | Sink  |
+---------+        +-------+ +-------+ +-------+    +---------+    +-------+
     |                 |         |         |             |             |
     | (Info)          |         |         |             |             |
     |---------------->|         |         |             |             |
     |                 |         |         |             |             |
     |                 | Discovery         |             |             |
     |                 |-------------------------------->|             |
     |                 |         |         |             |             |
     |                 |         |         |             | Mapping     |
     |                 |         |         |             |------------>|
     |                 |         |         |             |             |
     |                 |         |         |     Mapping |             |
     |                 |         |         |<------------|             |
     |                 |         |         |             |             |
     |                 |      (Provision)  |             |             |
     |                 |         |<--------|             |             |
     |                 |         |         |             |             |
     |                 |         Discovery |             |             |
     |                 |<------------------|             |             |
     |                 |         |         |             |             |
     |     (Provision) |         |         |             |             |
     |<----------------|         |         |             |             |
     |                 |         |         |             |             |
     |                 |         |         |             |    Pointset |
     |<----------------------------------------------------------------|
     |                 |         |         |             |             |
     | Pointset        |         |         |             |             |
     |---------------------------------------------------------------->|
     |                 |         |         |             |             |
```

1. Contains natively-encoded *Device* information (details out of scope for UDMI).
  * This could be, for example, a BACnet whois/iam exchange initiated from *Node* on a periodic timer
2. Discovery *Node* sends _discovery_ message.
  * In the case of BACnet, this would be the BACnet iam response transcoded to a UDMI discovery event
3. *Matcher* send a _mapping_ message (both *Sink* and *Agent* components receive).
  * Describes the exact _entity type_ and _IoT ID_ for the device as determined by the *Matcher*
4. The *Agent* sends a _discovery_ message containing the device's 'IoT ID'
  * This really _provisioning_ hidden inside of _discovery_, and might also contain necessary auth keys
5. The *Agent* can now setup the *Cloud* layer with the device _IoT ID_ to directly communicate with the device
  * Essentially, these are RPC calls to the IoT Core API to create a new device entry
5. Discovery *Node* provisions the *Device* using native protocols (if possible)
  * After this, the device should be able to communicate directly with the cloud
7. *Sink* can now send _pointset_ messages directly to the *Device*
  * This might, for example, configure the device with specific data points it should report
8. *Device* sends _pointset_ messages through to the data *Sink*
  * Including hopefully the intended data points that were just configured

## Gateway

The gateway (non-direct) version of this would largely look the same, except the device wouldn't
directly communicate with the cloud, and instead would always be proxied through an on-prem node,
which is likely the same physical component as the discovery *Node*.

## Source
Created using https://textart.io/sequence#
```
object Device Node Cloud Agent Matcher Sink
Device->Node: (Info)
Node->Matcher: Discovery
Matcher->Sink: Mapping
Matcher->Agent: Mapping
Agent->Cloud: (Provision)
Agent->Node: Discovery
Node->Device: (Provision)
Sink->Device: Pointset
Device->Sink: Pointset
```
