[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Family Providers](#)

# Family Provider URL Generation Specification

UDMI abstracts various fieldbus protocols (such as BACnet, Modbus, IP, etc.) through its `FamilyProvider` interface. A `FamilyProvider` is responsible for translating local protocol-specific addressing and metadata into standardized, globally unique Uniform Resource Locators (URLs) and vice-versa. 

These URLs provide a protocol-agnostic, structured path for identifying devices and data points:
`<family>://<device>/<point>`

This document details how a complete `FamilyProvider` URL is generated and resolved based on a device's metadata specification and how validation constraints enforce these rules.

---

## Core URL Structure

Every generated URL consists of three primary logical components:

| Component | Description | Mapping to Metadata | Example |
| :--- | :--- | :--- | :--- |
| **`family`** | The protocol/address family. | `gateway.target.family` | `bacnet` or `modbus` |
| **`device`** | The physical device address or host on that family. | `gateway.target.addr` | `1234` or `modbus_rtu_1` |
| **`point`** | The relative reference identifier of the data point. | `pointset.points.<point_name>.ref` | `AV:1` or `1/101?type=BOOLEAN` |

---

## URL Assembly Mechanics

The configuration manager uses a dynamic fallback strategy to resolve these components.

### 1. Device Address Selection (`device`)
The local device address `device` can be defined in one of two places, but **not both** (defining both raises a validation error):
- **Localnet Override**: `metadata.localnet.families.<family>.addr`
- **Gateway Target**: `metadata.gateway.target.addr`

The effective device address is resolved as:
`device_address = metadata.localnet.families.<family>.addr != null ? localnet.families.<family>.addr : gateway.target.addr`

*   **Host Syntax Constraint:** For families like `modbus`, the effective device address (the `<host>` component of the URL) must either **start with an alphabetical character** (e.g., `modbus_rtu_1` or `my-host`), or be formatted as a **valid 4-part IPv4 address** (e.g., `192.168.1.1` — starting with a number and having 4 octets separated by dots). A purely numeric, non-IP value like `"2"` is invalid as a host.

### 2. URL Combination Logic
For standard configurations, the system constructs the complete URL by concatenating the components:
`full_url = family + "://" + device_address + "/" + point_ref`

---

## Validation Constraints & Patterns

Because the validator constructs the URL programmatically as `family + "://" + device_address + "/" + point_ref`, the structure of your metadata determines whether point references can be relative or absolute.

### Pattern A: Combined Style (Target + Relative Reference)
In this pattern, a shared device/gateway target definition provides the default protocol family and target address for the device. The individual point definitions specify only their relative references. 

**This is the required pattern when a concrete gateway target family (e.g. `"bacnet"`, `"modbus"`) is specified.**

#### Example JSON Metadata (Combined Style)
```json
{
  "gateway": {
    "target": {
      "family": "bacnet",
      "addr": "1234"
    }
  },
  "pointset": {
    "points": {
      "fan_run_status": {
        "ref": "BI:1"
      },
      "supply_air_temp": {
        "ref": "AV:2"
      }
    }
  }
}
```
**Generated & Validated URLs:**
* `fan_run_status`: `bacnet://1234/BI:1`
* `supply_air_temp`: `bacnet://1234/AV:2`

---

### Pattern B: Ref-Only Style (Absolute / Self-Contained)
In this pattern, each individual point defines its full, absolute URL directly within its `ref` field. 

**CRITICAL VALIDATION RULE:** 
If a concrete target family (e.g. `"bacnet"`) is specified in `gateway.target.family`, the validator will attempt to combine it with a device address and validate the combined string.
* If a target address is defined, the combined URL becomes a nested string (e.g., `bacnet://1234/bacnet://5678/AV:1`), which fails the protocol-specific point regex validation (`BACNET_POINT`).
* If no target address is defined, the combined URL contains `null` (e.g., `bacnet://null/bacnet://5678/AV:1`), which fails address validation (`validateAddr("null")`).

Therefore, to use absolute, ref-only URLs, **the `gateway.target.family` must be omitted or left empty** (which defaults to `"vendor"` and disables strict protocol-level checks). Each point is then responsible for specifying its family, address, and path explicitly in its `ref` field.

#### Example JSON Metadata (Ref-Only Style)
```json
{
  "gateway": {
    "gateway_id": "LTGW-123"
  },
  "pointset": {
    "points": {
      "fan_run_status": {
        "ref": "bacnet://1234/BI:1"
      },
      "supply_air_temp": {
        "ref": "bacnet://1234/AV:2"
      }
    }
  }
}
```
**Generated & Validated URLs:**
* `fan_run_status`: `bacnet://1234/BI:1` (Validated under `"vendor"` as a generic reference with no restrictions)
* `supply_air_temp`: `bacnet://1234/AV:2` (Validated under `"vendor"` as a generic reference with no restrictions)

---

## Protocol-Specific Concrete Examples

The URL syntax for different address families is defined by their respective `FamilyProvider` implementations. Please refer to protocol-specific documentation (such as [bacnet.md](bacnet.md) and [modbus.md](modbus.md)) for detailed, exhaustive specifications of their reference formats and parameters.

### 1. BACnet Family (`bacnet`)
An address family representing BACnet devices and objects.
* **Combined Example:**
  * **Gateway Target Family**: `bacnet`
  * **Gateway Target Addr**: `1234`
  * **Point Ref**: `AV:100#present_value`
  * **Resulting URL**: `bacnet://1234/AV:100#present_value`

---

### 2. Modbus Family (`modbus`)
An address family representing Modbus registers.
* **Combined Example:**
  * **Gateway Target Family**: `modbus`
  * **Gateway Target Addr**: `modbus_rtu_1`
  * **Point Ref**: `2/3/40005?type=UINT32&worder=LWF&scale=0.01`
  * **Resulting URL**: `modbus://modbus_rtu_1/2/3/40005?type=UINT32&worder=LWF&scale=0.01`

---

## Advanced Deployment Topologies

The flexibility of absolute references enables advanced topologies where one logical UDMI "device" maps to multiple physical fieldbus targets or multiple protocols. 

**Because these topologies mix different target addresses or families, the `gateway.target.family` field must be left empty or omitted.** Every point must specify its full, absolute URL explicitly in its `ref` field.

### A. Multi-Address Devices (Single Protocol)
A multi-address device represents a single logical UDMI device whose points physically reside on different physical devices on the same local fieldbus network (e.g. multiple distinct BACnet devices).

#### Multi-Address Device Metadata Example
In the example below, `point_a` resides on BACnet device `1234`, while `point_b` physically resides on BACnet device `5678`.

```json
{
  "gateway": {
    "gateway_id": "LTGW-123"
  },
  "pointset": {
    "points": {
      "point_a": {
        "ref": "bacnet://1234/AV:1"
      },
      "point_b": {
        "ref": "bacnet://5678/AV:1"
      }
    }
  }
}
```

---

### B. Multi-Family Devices (Mixed Protocols)
A multi-family device represents a single logical UDMI device composed of points spanning different physical networks and entirely different protocol families (e.g., a combined Modbus and BACnet device).

#### Multi-Family Device Metadata Example
In the example below, the device collects data from a BACnet device (`1234`) and a Modbus device connected to the serial network `modbus_rtu_1`.

```json
{
  "gateway": {
    "gateway_id": "LTGW-123"
  },
  "pointset": {
    "points": {
      "room_temperature": {
        "ref": "bacnet://1234/AI:1"
      },
      "fan_power": {
        "ref": "modbus://modbus_rtu_1/2/3/40001/1?type=INT16"
      }
    }
  }
}
```
