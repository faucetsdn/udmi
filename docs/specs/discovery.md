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

## Named Scans

A device can run multiple independently scheduled scans, including more than one scan
of the same protocol family (e.g. a nightly periodic `bacnet` scan alongside an on-demand
targeted `bacnet` scan). Each entry in `config.discovery.scans` is a _named scan_, keyed by
a scan name matching `^[a-z][a-z0-9]*(_[a-z0-9]+)*$`.

* **Config**: Each [scan entry](../../gencode/docs/config.html#discovery_scans) specifies its
  target protocol `family` (required) along with scan parameters (`generation`,
  `scan_interval_sec`, `scan_duration_sec`, `depth`, `addrs`, `networks`, `passive_sec`, `trace`).
* **Scheduling**: Each named scan is scheduled independently according to the
  [Discovery Generation](discovery/generation.md) rules.
* **State**: Each named scan reports its progress in `state.discovery.scans.<name>`
  (`generation`, `phase`, `active_count`, `passive_count`, `status`).
* **Events**: All discovery events produced by a named scan (including start and termination
  marker events) carry the scan name in the event `scan` field alongside the target `family`.
* **Lifecycle**: Removing a named scan from `config.discovery.scans` stops it and removes its
  `state.discovery.scans.<name>` entry. Changing the `family` of a named scan stops the old
  scan and starts a new one. Applying a config identical to the current one has no effect.
* **Errors**: Whether two scans targeting the same family can run concurrently is up to the
  device. When a scan's start time arrives while its target family is already active:
  * A **sporadic** (one-time) scan remains in `phase` `pending` (with no error `status`) and
    starts automatically once the family becomes available.
  * A **periodic** scan skips that slot and schedules its next upcoming interval slot (`phase`
    `pending`).
  When a device cannot run a named scan because its configuration is invalid or unsupported
  (such as missing `family` or naming an unsupported `family`) or fails to start, it sets that
  entry's `phase` to `stopped` with a `status` in category `discovery.family.scan` at level
  `ERROR` (500) explaining why, leaving other scans unaffected.
* **Compatibility**: `config.discovery.families.<family>` remains supported as the legacy
  single-scan-per-family form (in a separate namespace from `scans`), where the map key is the
  target family, `family` must be omitted in config, and `scan` is omitted on emitted events.
  New configurations should prefer `config.discovery.scans`.

Example config with two named `bacnet` scans:

<!--example:config/discovery_scans.json-->
```json
//
// MQTT topic: /devices/{device_id}/config
// where {device_id} is that of the discovery node.
//
{
  "version": "1.5.7",
  "timestamp": "2018-08-26T21:39:29.364Z",
  "discovery": {
    "scans": {
      "bacnet_nightly": {
        "family": "bacnet",
        "generation": "2018-08-26T02:00:00Z",
        "scan_interval_sec": 86400,
        "scan_duration_sec": 600,
        "depth": "entries"
      },
      "bacnet_targeted": {
        "family": "bacnet",
        "generation": "2018-08-26T21:39:00Z",
        "scan_duration_sec": 60,
        "addrs": [
          "1000-1099"
        ],
        "depth": "entries"
      }
    }
  }
}
```
Corresponding device state (with `bacnet_nightly` scheduled for its next `02:00Z` slot):

<!--example:state/discovery_scans.json-->
```json
//
// Device sends on MQTT topic: /devices/{device_id}/state
// where {device_id} is that of the discovery node.
//
{
  "version": "1.5.7",
  "timestamp": "2018-08-26T21:39:29.364Z",
  "system": {
    "hardware": {
      "make": "ACME",
      "model": "Bird Trap"
    },
    "software": {
      "firmware": "3.2a"
    },
    "serial_no": "182732142",
    "last_config": "2018-08-26T21:39:29.364Z",
    "operation": {
      "operational": true
    }
  },
  "discovery": {
    "scans": {
      "bacnet_nightly": {
        "generation": "2018-08-27T02:00:00Z",
        "phase": "pending"
      },
      "bacnet_targeted": {
        "generation": "2018-08-26T21:39:00Z",
        "phase": "active",
        "active_count": 3
      }
    }
  }
}
```
Discovery event produced by the `bacnet_targeted` scan:

<!--example:events_discovery/scan_named.json-->
```json
//
// Sent on MQTT topic: /devices/{device_id}/events/discovery
// where {device_id} is that of the discovery node. The
// scan field names the configured scan that produced this result.
//
{
  "version": "1.5.7",
  "timestamp": "2018-08-26T21:39:29.364Z",
  "generation": "2018-08-26T21:39:00Z",
  "family": "bacnet",
  "scan": "bacnet_targeted",
  "addr": "1042",
  "event_no": 3,
  "families": {
    "ipv4": {
      "addr": "192.168.1.42",
      "port": 47808
    }
  }
}
```
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

Site model discovery exports the cloud-managed device metadata models for a registry using the
standard discovery event stream contract. Requests and emitted messages set `subFolder` to
`discovery`, `subSetKey` to `model`, and `providerId` to the backend service identifier with
`deviceId` omitted on the message [envelope](../messages/envelope.md).

A full-site model discovery scan for a registry with `N` active devices emits a deterministic,
ordered sequence of `events/discovery` messages sharing the same `generation` timestamp and
`iot` family:

1. **Start marker** with `event_no: 0`: Signals the start of the scan stream for `generation`.
2. **Device model events** with `event_no: 1 .. N`: One event per active device in sorted
   `deviceId` order, with `addr` set to the device ID and `model` populated with the device's
   [metadata](metadata.md).
3. **End marker** with `event_no: -(N + 1)`: Signals completion of the scan stream. When a
   registry has zero active devices, `event_no: 0` is followed immediately by `event_no: -1`.
