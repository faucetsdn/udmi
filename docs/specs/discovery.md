[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Discovery](#)

# Discovery

Discovery is the first phase in the overall [Onboarding](onboarding.md) flow, followed by [Mapping](mapping.md).

Discovery consists of two related processes for describing the 'as built'
state of a system: _scanning_ and _enumeration_. For devices, the overall
[discovery sequence](sequences/discovery.md) describes the exact sequence
of device messages employed in each case. Each process can be
executed independently, or together (known as _scan enumeration_):

* _scanning_ scans for existing devices, and returns information about
their discovered address families. This is
information about how the device is indexed in the world around it.

* _enumeration_ lists the properties of a given target device. Providing
information intrinsic to a device and the capabilities it provides.

Backend services will receive a streaming set of
[_discovery enumeration messages_](../../tests/schemas/events_discovery/enumeration.json) that
follow the appropriate [_discovery event schema_](../../gencode/docs/events_discovery.html).

## Scanning

_Scanning_ is the process of scanning a network and identifying the various
entities thereof. Often (but not always), this comes along with a correlation
of various address families (e.g. IPv4 address associated with a particular MAC):

* ethmac (_82:CC:18:9A:45:1C_): Ethernet mac address for low-level networking
* ipv4 (_10.27.38.123_): Assigned IPv4 device network address
* ipv6 (_FE80::8E8C:BC72_): Assigned IPv6 device network address
* bacnet (_92EA09_): The device's BACnet mac-address
* iot (_AHU-32_): Device designation as used in cloud-native processing
* host (_AHU-32.ACME.COM_): DNS hostname for a device

Scanning results can only describe a subset of the complete picture (e.g. only the
_ETHMAC_ and _IPv4_ address), and it is up to the back-end systems to properly link/infer complete
relationships. Some systems may only care about singular entries (e.g. just discovering
what IoT devices are there, but not caring about any association).

Discovery is a process that can be explicitly requested through UDMI for on-prem
devices that support the capability (e.g. an [IoT Gateway](gateway.md)), or it
can be done automatically by a device itself (e.g. on a predefined interval). Depending
on device capabilities and system configuration, the scanning process may also
trigger discovered device enumeration.

For details on how the `generation` field operates during different scan types, see the [Discovery Generation](discovery/generation.md) documentation. For protocol-specific details on active BACnet discovery and scan depths, see the [BACnet Discovery](discovery_bacnet.md) specification.

## Discovery Depth

Discovery `depth` controls the level of detail collected during a scan. Each depth level is cumulative, building upon the preceding level:

* **`buckets`**: High-level network segments or device groupings.
* **`entries`**: Individual discovered devices and cross-family address associations.
* **`system`**: Device identity and hardware information.
* **`details`**: Enumerated device points, objects, or services.

| `depth` | Description | `bacnet` ([Spec](discovery_bacnet.md)) | `iot` | `ipv4` |
| :--- | :--- | :--- | :--- | :--- |
| **`buckets`** | Network or logical groupings | BACnet networks and UDP ports | Cloud registries or site groups | IPv4 subnets |
| **`entries`** | Device addresses and bindings | Device instances and IP/MAC bindings | IoT device IDs and gateway bindings | Host IP, MAC, and hostname bindings |
| **`system`** | Device identity and hardware | Device object identity and vendor info | Reported system hardware and software | Host OS and hardware fingerprint |
| **`details`** | Exposed points or services | BACnet objects and properties | Configured or self-enumerated points | Open ports and network services |

## Enumeration

_Enumeration_ is the process for listing  _all_ the parameters available from a device
(rather than just the ones in its designated reporting set). This information can
either come directly from a device (_self_ enumeration) or as the result of a discovery
scan (_scan_ enumeration). Both report the same kind of content, but the mechanism
(and message source) are different: one comes from the device itself, the other by proxy.

Within an enumeration message, there's a number of different kinds of information that can
be reported:
  * `refs`: A listing of all the data points that a device has to offer, indexed by their
  protocol-specific reference. References are curated into named `points`, which forms
  the foundation of the `pointset` messages.
  * `blobs`: A listing of all the data blobs that a device knows how to handle. This could
  be components like firmware updates, key rotation, etc... Some blobs will be standardized
  across the system, while others will be device-specific.

## Site Model Discovery

Site Model Discovery exposes the cloud-registered device models of a registry over the standard UDMI discovery pipeline (`family: "iot"`, `source: "model"`). It is triggered automatically when a registry model update is processed by UDMIS, or on-demand by publishing a registry-scoped [`config`](../../gencode/docs/config.html) discovery message (e.g., on `/uufi/r/{deviceRegistryId}/c/config/discovery` with `families.iot.source` set to `"model"`).

The Site Model Discovery sequence follows the standard discovery state and event bookends:

1. **Start State (`state/discovery`):** Published at the registry level with `source: "model"`, `families.iot.phase: "active"`, and `families.iot.active_count: 0`.
2. **Start & Count Event (`events/discovery`):** Published at the registry level with `source: "model"`, `event_no: 0`, `family: "iot"`, and `active_count` set to the total number of matching devices in the registry.
3. **Device Model Events (`events/discovery`):** When `depth` is `details` (or `parts`, or omitted), one event per device (`event_no: 1..N`) is published with `source: "model"`, `family: "iot"`, `addr` set to the device ID, and `model` populated with the device's [`metadata`](../../gencode/docs/metadata.html) object. When `depth` is `entries`, individual device events are omitted and only the `event_no: 0` count is emitted. If `addrs` is specified in `families.iot`, only matching device IDs are included.
4. **Stop State (`state/discovery`):** Published at the registry level with `source: "model"`, `families.iot.phase: "stopped"`, and `families.iot.active_count` set to the total number of discovered devices.
