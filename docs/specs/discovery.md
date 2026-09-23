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

The `depth` setting (`config.discovery.families.<family>.depth` for network scans, or `config.discovery.depth` for self-enumeration) controls the granularity of information collected and reported in [`events_discovery`](../../schema/events_discovery.json) messages.

The active discovery depths are ordered cumulatively from coarse network topology to full point or service enumeration:

* **`buckets`**: Identifies high-level network segments, subnets, or device registries (`network`) without enumerating individual devices.
* **`entries`**: Discovers individual device addresses (`addr`) within each bucket and correlates cross-family transport addresses (`families`, such as IPv4 endpoints and Ethernet MAC addresses).
* **`system`**: Queries each discovered device for its system identity and hardware metadata (`system.name`, `system.description`, `system.serial_no`, `system.hardware.make`, `system.hardware.model`, and `system.ancillary`).
* **`details`**: Enumerates the full set of data points, objects, or services exposed by each device (`refs`), including names, data types, units, writable flags, and operational values.

### Depth Across Core Discovery Providers

The table below summarizes what each `depth` level includes across the core discovery families (`bacnet`, `iot`, and `ipv4`):

| `depth` | General Scope | `bacnet` ([Spec](discovery_bacnet.md)) | `iot` | `ipv4` |
| :--- | :--- | :--- | :--- | :--- |
| **`buckets`** | Network segments or logical containers (`network`) | Available BACnet network numbers (`network`) and BACnet/IP UDP ports (`families.ipv4.port`) discovered via `Who-Is-Router-To-Network` | Cloud or broker device registries and logical IoT site groups (`network`) | Configured or routed IPv4 subnets and local network segments (`network`) |
| **`entries`** | Individual device addresses (`addr`) and transport bindings (`families`) | Discovered BACnet Device Instance numbers (`addr`) correlated with source IP/UDP port and MAC (`families.ipv4`, `families.ethmac`) via `Who-Is` / `I-Am` | Individual IoT device IDs (`addr`) and associated gateway or localnet addresses (`families`) | Active IPv4 host addresses (`addr`) correlated with neighbor Ethernet MAC and DNS hostname bindings (`families.ethmac`, `families.host`) |
| **`system`** | Device identity and hardware metadata (`system`) | BACnet Device Object (`DO/0`) identity (`object-name`, `vendor-name`, `model-name`, `serial-number`, `firmware-revision`) | Reported UDMI `state.system` identity (`hardware.make`, `hardware.model`, `serial_no`, `software`) | Host operating system, banner identity, and hardware fingerprint (`system.name`, `system.hardware`, `system.ancillary`) |
| **`details`** | Point, object, or service enumeration (`refs`) | Enumerated BACnet `object-list` (`AI/2`, `AV/12`, `BO/21`, etc.) with `name`, `type`, `units`, `writable`, and `present-value` | Self-enumerated or configured UDMI telemetry points (`refs` with `name`, `units`, `type`, `writable`) | Open `TCP`/`UDP` ports and exposed network services (`refs` indexed by port/service with protocol metadata) |

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
