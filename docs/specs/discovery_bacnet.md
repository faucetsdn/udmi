[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [BACnet Discovery](#)

# BACnet Discovery Specification

This document specifies how an on-premise UDMI discovery node (Spotter / Gateway) performs **active BACnet discovery** (`config.discovery.families.bacnet`) and formats the resulting [`events_discovery`](../../schema/events_discovery.json) messages across each valid scan `depth`.

For static BACnet URI addressing rules (`bacnet://<device_id>/<object_type>/<object_instance>[#property_identifier]`), see the [BACnet Specification](bacnet.md) and [Family Providers Specification](family_providers.md). For general, protocol-agnostic discovery scheduling (`generation`, `scan_interval_sec`, `scan_duration_sec`), see [Discovery](discovery.md) and [Discovery Generation](discovery/generation.md).

* **Reference**: [The Language of BACnet — Objects, Properties and Services](https://bacnet.org/wp-content/uploads/sites/4/2022/06/The-Language-of-BACnet-1.pdf)

---

## 1. Active Scan `depth` Levels

A BACnet active discovery scan is controlled by `config.discovery.families.bacnet.depth`. There are **4 valid active scan depth levels**, ordered cumulatively from coarsest network topology discovery to full object property enumeration:

| Ordinal | `depth` Value | Scope | BACnet Service / Property Operations | Cumulative `events_discovery` Payload Fields |
| :--- | :--- | :--- | :--- | :--- |
| **1** | **`buckets`** | Available BACnet networks & UDP ports | `Who-Is-Router-To-Network` / `I-Am-Router-To-Network` + BACnet/IP UDP port probe (`47808` / `0xBAC0`, etc.) | Base envelope + `family: "bacnet"` + `network` + `families.ipv4.port` |
| **2** | **`entries`** | Discovered BACnet devices & MAC/IP bindings | `Who-Is` / `I-Am` (global, network-filtered, or unicast `addrs`) | `buckets` + `addr` (Device Instance) + per-device `families` (`ipv4` `addr`/`port`, `ethmac`, `mstp`) |
| **3** | **`system`** | Device Object (`DO/0`) identity properties | `ReadPropertyMultiple` on `device,<addr>` (`object-name`, `vendor-name`, `model-name`, `serial-number`, etc.) | `entries` + `system` (`name`, `description`, `serial_no`, `hardware`, `ancillary`) |
| **4** | **`details`** | BACnet object references & properties | `ReadProperty` / `ReadPropertyMultiple` on `device,<addr>` `object-list` + per-object `(object-type, instance)` properties | `system` + `refs` map (`"<TYPE>/<INSTANCE>"` $\rightarrow$ populated `RefDiscovery` fields: `name`, `description`, `type`, `units`, `possible_values`, `writable`, `ancillary`) |

Each level is strictly additive: requesting a deeper level produces all fields required by the preceding level plus the additional fields defined for that depth.

---

## 2. Scan Targeting & Scope Filters

Before executing the depth-specific operations below, the discovery node scopes the BACnet probe using `config.discovery.families.bacnet`:

* **`networks`** (Optional `string[]` of 16-bit BACnet network numbers, `"1"`..`"65534"`): Restricts `Who-Is` broadcasts and device enumeration (`entries` and above) to the specified BACnet network numbers (`DNET`). If omitted, all reachable networks are scanned.
* **`addrs`** (Optional `string[]` of BACnet Device Object Instance numbers or ranges): Restricts discovery to the specified BACnet device instances. Each string element in `addrs` MUST be either:
  * **An exact Device Object Instance number** (`"<instance>"`, e.g., `"291842"`), which issues a `Who-Is` with `deviceInstanceRangeLowLimit = deviceInstanceRangeHighLimit = <instance>`.
  * **An inclusive hyphen-separated instance range** (`"<low>-<high>"`, e.g., `"1000-1999"`), which issues a bounded `Who-Is` with `deviceInstanceRangeLowLimit = <low>` and `deviceInstanceRangeHighLimit = <high>` (where `1 <= low <= high <= 4194303` with no leading zeroes).
  * Example: `"addrs": ["1000-1999", "291842", "500000-500999"]`.

---

## 3. Detailed `depth` Specifications & Cumulative JSON Payloads

### 3.1 `buckets` — Available BACnet Networks & BACnet/IP UDP Ports

At the `buckets` depth, the discovery node discovers the available BACnet network segments and their correlated BACnet/IP UDP ports (e.g., standard `47808` [`0xBAC0`], `47809` [`0xBAC1`], etc.) without enumerating individual devices on those networks.

* **BACnet Operations**:
  * Broadcasts `Who-Is-Router-To-Network` across configured BACnet/IP UDP ports (`0xBAC0`..`0xBACF`) and collects `I-Am-Router-To-Network` responses, combined with local interface BACnet network numbers.
* **UDMI Output**:
  * Emits one `events_discovery` message per discovered `(network, port)` bucket:
    * `network`: The 16-bit BACnet network number (`"1"`..`"65534"`).
    * `families.ipv4.port`: The correlated UDP port number for the BACnet/IP network (defined in [`schema/discovery_family.json`](../../schema/discovery_family.json#L14-L17) specifically as `"Port number for the family connection (e.g. UDP port for BACnet/IP)"`), along with the router/broadcast `addr` if discovered via a BACnet/IP router.
  * Device-level `addr`, `system`, and `refs` are omitted.

#### Base JSON Payload (`depth: "buckets"`)
```json
{
  "version": "1.5.7",
  "timestamp": "2026-09-22T14:50:05Z",
  "generation": "2026-09-22T14:50:00Z",
  "family": "bacnet",
  "network": "9288",
  "event_no": 1,
  "families": {
    "ipv4": {
      "port": 47808
    }
  }
}
```

---

### 3.2 `entries` — Discovered BACnet Devices & MAC/Transport Addresses

At the `entries` depth, the discovery node identifies individual BACnet devices on each network bucket and correlates each device's BACnet instance with its specific transport IP/UDP port and MAC address.

* **Relative to `buckets`**:
  * The same base envelope as `buckets` (emitted once per discovered BACnet device rather than once per network bucket), **with the addition of**:
    * `addr`: The device's 22-bit BACnet Device Object Instance number (`"1"`..`"4194303"`).
    * `families.ipv4.addr`: The specific device's IPv4 address paired with its BACnet/IP UDP `port` (`47808`).
    * `families.ethmac` (or `mstp`): Lower-layer MAC address observed in the `I-Am` NPDU/APDU source header.
* **BACnet Operations**:
  * Issues `Who-Is` (unconstrained, bounded by `networks`, or targeted by `addrs`) and records each responder's `I-Am` `iAmDeviceIdentifier` instance number (`addr`), source network (`network`), and source IP/UDP/MAC endpoint (`families`).

#### JSON Additions Relative to `buckets` (`depth: "entries"`)
```json
{
  "addr": "291842",
  "families": {
    "ipv4": {
      "addr": "192.168.10.45",
      "port": 47808
    },
    "ethmac": {
      "addr": "82:CC:18:9A:45:1C"
    }
  }
}
```

---

### 3.3 `system` — Device Object (`DO/0`) Properties

At the `system` depth, the discovery node queries the BACnet **Device Object** (`object-type = device (8)`, `instance = <addr>`) of each discovered device to populate hardware and identity metadata.

* **Relative to `entries`**:
  * The same as `entries`, **with the addition of** the `system` block populated from the BACnet Device Object's standard properties:
    * `system.name` <- `object-name` (Property ID 77)
    * `system.description` <- `description` (Property ID 28)
    * `system.serial_no` <- `serial-number` (Property ID 372, if supported by device)
    * `system.hardware.make` <- `vendor-name` (Property ID 121)
    * `system.hardware.model` <- `model-name` (Property ID 70)
    * `system.ancillary` <- Additional Device Object properties (`firmware-revision` [44], `application-software-version` [12], `location` [58], `vendor-identifier` [120], `protocol-version` [98], `protocol-revision` [139]).
* **BACnet Operations**:
  * Issues `ReadPropertyMultiple` against `device,<addr>` (falling back to individual `ReadProperty` requests if segmentation or `ReadPropertyMultiple` is unsupported).

#### JSON Additions Relative to `entries` (`depth: "system"`)
```json
{
  "system": {
    "name": "AHU-1",
    "description": "Main North Wing Air Handler",
    "serial_no": "SN-99482104",
    "hardware": {
      "make": "Delta Controls",
      "model": "DSC-1616E"
    },
    "ancillary": {
      "firmware-revision": "4.10r3",
      "application-software-version": "1.2.0",
      "location": "Mechanical Room 2B",
      "vendor-identifier": 8
    }
  }
}
```

---

### 3.4 `details` — BACnet Object References & Properties

At the `details` depth, the discovery node enumerates the device's `object-list` (Property ID 76 on `device,<addr>`) and reads the descriptive and operational BACnet properties for each object.

* **Relative to `system`**:
  * The same as `system`, **with the addition of** the `refs` map containing each discovered BACnet object's canonical reference key (`"<OBJECT_TYPE>/<INSTANCE>"`, e.g., `"AI/2"`, `"AV/12"`, `"BO/21"`, `"MSV/1"`, matching `BacnetFamilyProvider` and [docs/specs/bacnet.md](bacnet.md)) mapped to a populated [`RefDiscovery`](../../schema/discovery_ref.json) object:
    * `name` <- `object-name` (Property ID 77)
    * `description` <- `description` (Property ID 28)
    * `type` <- Standard BACnet `object-type` name (`"analog-input"`, `"analog-output"`, `"analog-value"`, `"binary-input"`, `"binary-output"`, `"binary-value"`, `"multi-state-input"`, `"multi-state-output"`, `"multi-state-value"`, etc.)
    * `units` <- `units` (Property ID 117, for analog objects)
    * `possible_values` <- `[inactive-text, active-text]` (Property IDs 46, 4 for binary objects) or `state-text` array (Property ID 110 for multi-state objects)
    * `writable` <- `true` for output objects (`AO`, `BO`, `MSO`) and commandable value objects (`AV`, `BV`, `MSV` where `priority-array` [Property ID 87] is present); `false` for input objects (`AI`, `BI`, `MSI`) and non-commandable value objects
    * `ancillary` <- Operational and diagnostic BACnet object properties:
      * `present-value` (Property ID 85)
      * `status-flags` (Property ID 111)
      * `event-state` (Property ID 36)
      * `out-of-service` (Property ID 81)
      * `reliability` (Property ID 103)
      * `cov-increment` (Property ID 22, if applicable)
      * `min-pres-value` / `max-pres-value` / `resolution` (Property IDs 69, 65, 106, if applicable)

#### JSON Additions Relative to `system` (`depth: "details"`)
```json
{
  "refs": {
    "AI/2": {
      "name": "ZN-T",
      "description": "Zone Space Temperature",
      "type": "analog-input",
      "units": "degrees-celsius",
      "writable": false,
      "ancillary": {
        "present-value": 21.5,
        "status-flags": [false, false, false, false],
        "event-state": "normal",
        "out-of-service": false,
        "cov-increment": 0.1
      }
    },
    "AV/12": {
      "name": "ZN-SP",
      "description": "Zone Temperature Setpoint",
      "type": "analog-value",
      "units": "degrees-celsius",
      "writable": true,
      "ancillary": {
        "present-value": 22.0,
        "status-flags": [false, false, false, false],
        "out-of-service": false
      }
    },
    "BO/21": {
      "name": "SF-CMD",
      "description": "Supply Fan Start/Stop Command",
      "type": "binary-output",
      "possible_values": ["Off", "On"],
      "writable": true,
      "ancillary": {
        "present-value": "On",
        "status-flags": [false, false, false, false],
        "out-of-service": false
      }
    },
    "MSV/1": {
      "name": "OCC-MODE",
      "description": "Zone Occupancy Mode",
      "type": "multi-state-value",
      "possible_values": ["Occupied", "Unoccupied", "Standby", "Bypass"],
      "writable": true,
      "ancillary": {
        "present-value": 1,
        "status-flags": [false, false, false, false]
      }
    }
  }
}
```
