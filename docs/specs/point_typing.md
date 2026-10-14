[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Point Typing](#)

# Point Typing Specification

This specification defines how UDMI classifies telemetry and control points into **Numeric**, **Multi-state**, and **Binary** types based on the device's site model ([`metadata.json`](metadata.md)), and how that classification governs the JSON payload structure of [`pointset`](../messages/pointset.md) messages (`events`, `config`, and `state`).

---

## 1. Overview and Classification Rules

Every point declared under `pointset.points.<point_name>` in a device's `metadata.json` is deterministically classified into one of three mutually exclusive point types—**Numeric**, **Multi-state**, or **Binary**—based on the presence of the `units` and `values` fields:

| Point Type | Site Model Condition (`pointset.points.<point_name>`) | Wire JSON Type (`present_value` & `set_value`) | Valid Payload Examples | Invalid Payload Examples |
| :--- | :--- | :--- | :--- | :--- |
| **Numeric** | `units` is defined; `values` is omitted | `number` (integer or floating-point) | `21.5`, `100`, `0`, `-4.2` | `"21.5"`, `true`, `"HIGH"` |
| **Multi-state** | `values` is defined; `units` is omitted | `string` (canonical state in `values`) | `"OFF"`, `"ON"`, `"OCCUPIED"`, `"AUTO"` | `0`, `1.0`, `true`, `"0.0"` |
| **Binary** | Neither `units` nor `values` is defined | `boolean` (`true` or `false`) | `true`, `false` | `0`, `1`, `"true"`, `"ON"` |
| *Invalid* | Both `units` and `values` are defined | *Validation Error* | N/A | N/A |

```mermaid
flowchart TD
    A["Point definition in metadata.json<br/>pointset.points.&lt;point_name&gt;"] --> B{"Are both 'units' and<br/>'values' defined?"}
    B -- Yes --> ERR["Validation Error:<br/>'units' and 'values' are mutually exclusive"]
    B -- No --> C{"Is 'units' defined?"}
    C -- Yes --> NUM["Type: NUMERIC<br/>Wire Payload: JSON number"]
    C -- No --> D{"Is 'values' defined?"}
    D -- Yes --> MS["Type: MULTI-STATE<br/>Wire Payload: JSON string (mapped state)"]
    D -- No --> BIN["Type: BINARY<br/>Wire Payload: JSON boolean (true / false)"]
```

### Mutual Exclusivity and Model Constraints

1. **Mutual Exclusivity**: A point definition in `metadata.json` MUST NOT specify both `units` and `values`. Specifying both is a site model validation error.
2. **Non-Empty Definitions**:
   * When `units` is present, it MUST be a non-empty string designating the unit of measure (or an explicit dimensionless unit designator such as `"no-units"`).
   * When `values` is present, it MUST be a non-empty object mapping at least two raw device values to canonical state strings.
3. **Default to Binary**: When a point definition omits both `units` and `values`, the point is unconditionally typed as **Binary**.

---

## 2. Type Specifications

### 2.1 Numeric Points (`units` Defined)

A point is **Numeric** when `pointset.points.<point_name>.units` is present and `values` is absent.

#### Site Model (`metadata.json`)
* **`units`** *(required for Numeric)*: Specifies the canonical unit of measure expected for the point (e.g., `"degrees-celsius"`, `"percent"`, `"kilowatts"`, `"liters_per_second"`).
* **Dimensionless Numeric Quantities**: Points that represent numerical counts, ratios, or other dimensionless numbers (such as request counts, cycle counts, or power factor) MUST still define `units` using the dimensionless designator `"no-units"` (or `"No-units"`). The presence of `units` classifies the point as **Numeric** rather than **Binary**.
* **`units_reported`** *(optional)*: Specifies the raw unit string reported by the underlying device if it differs from the canonical `units` string.
* **Numeric Constraints** *(optional)*: `baseline_value` (must be a JSON `number`), `baseline_tolerance`, `range_min`, `range_max`, and `cov_increment` apply exclusively to Numeric points.

#### Wire Payloads
* **Telemetry (`events_pointset`)**:
  * `points.<point_name>.present_value` MUST be serialized as a native JSON `number` (integer or floating-point, e.g., `21.5` or `4`).
  * Whole-number quantities (such as integer counters) should be serialized without a fractional decimal part (e.g., `4`), and continuous measurements as floating-point numbers (e.g., `21.5`).
  * Serializing a numeric reading as a quoted string (e.g., `"21.5"`) or boolean is a validation error.
* **Configuration & Writeback (`config_pointset`)**:
  * `pointset.points.<point_name>.units` communicates the operating unit to the client.
  * When writeback is active, `pointset.points.<point_name>.set_value` MUST be a JSON `number`. If a non-numeric `set_value` or a value outside the device's valid operating bounds is received, the client MUST reject the writeback and report `value_state: "invalid"` in `state.pointset.points.<point_name>`.

---

### 2.2 Multi-state Points (`values` Defined)

A point is **Multi-state** when `pointset.points.<point_name>.values` is present and `units` is absent.

#### Site Model (`metadata.json`)
* **`values`** *(required for Multi-state)*: A JSON object mapping each underlying raw device value (the object key, formatted as a string such as `"0.0"`, `"1.0"`, `"1"`, `"2"`) to its canonical ontology state label (the string value, formatted as an uppercase string such as `"OFF"`, `"ON"`, `"ACTIVE"`, `"INACTIVE"`, `"OCCUPIED"`, `"UNOCCUPIED"`, `"STANDBY"`):
  ```json
  "values": {
    "1": "OCCUPIED",
    "2": "UNOCCUPIED",
    "3": "STANDBY"
  }
  ```
  If multiple raw device values correspond to the same canonical state, each raw value is listed as its own key mapping to that canonical state string:
  ```json
  "values": {
    "0.0": "OFF",
    "1.0": "ON",
    "2.0": "ON"
  }
  ```
* **Two-State Enumerations**: Any point that defines `values` is classified as **Multi-state**, even when `values` has only two entries (for example, `{"0.0": "OFF", "1.0": "ON"}` or `{"1.0": "ACTIVE", "0.0": "INACTIVE"}`). Because explicit state labels are declared in `values`, the point's wire representation is a JSON `string` using those canonical state labels (`"OFF"`, `"ON"`), not a JSON `boolean`.
* **Constraints**: `baseline_value`, when present on a Multi-state point, MUST be a JSON `string` matching one of the canonical state values in `values`. Numeric-only fields (`units`, `range_min`, `range_max`, `baseline_tolerance`, `cov_increment`) MUST NOT be set on a Multi-state point.

#### Wire Payloads and Client Translation
* **Telemetry (`events_pointset`)**:
  * `points.<point_name>.present_value` MUST be serialized as a JSON `string` that matches one of the canonical state strings (map values) defined in `values` (e.g., `"OCCUPIED"`, `"ON"`).
  * When a client reads a raw ordinal or native value from its underlying hardware/protocol interface, it MUST look up that raw key in `values` and publish the mapped canonical state string in `present_value`. Raw ordinals (e.g., `1`, `"1.0"`, `"0.0"`) MUST NOT be emitted as `present_value`.
  * If the underlying hardware reports a raw value that does not match any key in `values`, the client MUST omit `present_value` for that point (or mark the point invalid) and populate `state.pointset.points.<point_name>.status` with an error entry.
* **Configuration & Writeback (`config_pointset`)**:
  * When writeback is active, `pointset.points.<point_name>.set_value` MUST be a JSON `string` matching a canonical state value in `values`.
  * The client performs a reverse lookup in `values` to translate the canonical state string in `set_value` to the corresponding raw device key to actuate the point.
  * If `set_value` is not a string or does not match any canonical state value in `values`, the client MUST reject the writeback and set `state.pointset.points.<point_name>.value_state` to `"invalid"`.

---

### 2.3 Binary Points (Neither `units` Nor `values` Defined)

A point is **Binary** when `pointset.points.<point_name>` defines neither `units` nor `values`.

#### Site Model (`metadata.json`)
* Both `units` and `values` are omitted from `pointset.points.<point_name>`.
* Standard non-typing metadata properties (`ref`, `writable`, `baseline_value`, `baseline_state`, `description`, `tags`, `expr`) may still be present.
* **Constraints**: If `baseline_value` is specified for a Binary point, it MUST be a JSON `boolean` (`true` or `false`). Numeric-only fields (`units`, `range_min`, `range_max`, `baseline_tolerance`, `cov_increment`) MUST NOT be set.

#### Wire Payloads
* **Telemetry (`events_pointset`)**:
  * `points.<point_name>.present_value` MUST be serialized as a JSON `boolean` literal (`true` or `false`).
  * Numeric representations (`0`, `1`) and string literals (`"true"`, `"false"`, `"ON"`, `"OFF"`) are invalid for Binary points.
* **Configuration & Writeback (`config_pointset`)**:
  * When writeback is active, `pointset.points.<point_name>.set_value` MUST be a JSON `boolean` (`true` or `false`).
  * If `set_value` is any type other than `boolean`, the client MUST reject the writeback and report `value_state: "invalid"` in `state.pointset.points.<point_name>`.

---

## 3. End-to-End Example

### 3.1 Site Model Definition (`devices/AHU-1/metadata.json`)

The following `pointset` block in `metadata.json` illustrates all three point types—including a continuous Numeric sensor, a dimensionless Numeric counter, a two-state Multi-state command, a four-state Multi-state mode, and a Binary status/command point:

```json
{
  "version": "1.5.7",
  "timestamp": "2026-01-01T00:00:00Z",
  "pointset": {
    "points": {
      "supply_air_temperature_sensor": {
        "units": "degrees-celsius",
        "ref": "analog_input_1",
        "baseline_value": 21.5,
        "baseline_tolerance": 2.0
      },
      "cooling_request_count": {
        "units": "no-units",
        "ref": "analog_value_4"
      },
      "supply_fan_run_command": {
        "writable": true,
        "ref": "digital_output_2",
        "values": {
          "0.0": "OFF",
          "1.0": "ON"
        },
        "baseline_value": "ON"
      },
      "occupancy_mode": {
        "writable": true,
        "ref": "multistate_value_1",
        "values": {
          "1": "OCCUPIED",
          "2": "UNOCCUPIED",
          "3": "STANDBY",
          "4": "BYPASS"
        },
        "baseline_value": "OCCUPIED"
      },
      "filter_alarm_pressure_status": {
        "ref": "binary_input_11",
        "baseline_value": false
      },
      "damper_isolation_enable": {
        "writable": true,
        "ref": "binary_output_5",
        "baseline_value": true
      }
    }
  }
}
```

### 3.2 Resulting Config Message (`config` -> `pointset`)

When the cloud configures the device (including writeback `set_value` entries derived from `baseline_value` for writable points), the generated `config.pointset` block is:

```json
{
  "sample_rate_sec": 300,
  "points": {
    "supply_air_temperature_sensor": {
      "ref": "analog_input_1",
      "units": "degrees-celsius"
    },
    "cooling_request_count": {
      "ref": "analog_value_4",
      "units": "no-units"
    },
    "supply_fan_run_command": {
      "ref": "digital_output_2",
      "set_value": "ON"
    },
    "occupancy_mode": {
      "ref": "multistate_value_1",
      "set_value": "OCCUPIED"
    },
    "filter_alarm_pressure_status": {
      "ref": "binary_input_11"
    },
    "damper_isolation_enable": {
      "ref": "binary_output_5",
      "set_value": true
    }
  }
}
```

### 3.3 Resulting Pointset Event Payload (`events/pointset`)

The client publishes the corresponding `events/pointset` telemetry message with each point's `present_value` formatted according to its resolved type:

```json
{
  "version": "1.5.7",
  "timestamp": "2026-01-01T00:05:00Z",
  "points": {
    "supply_air_temperature_sensor": {
      "present_value": 21.4
    },
    "cooling_request_count": {
      "present_value": 3
    },
    "supply_fan_run_command": {
      "present_value": "ON"
    },
    "occupancy_mode": {
      "present_value": "OCCUPIED"
    },
    "filter_alarm_pressure_status": {
      "present_value": false
    },
    "damper_isolation_enable": {
      "present_value": true
    }
  }
}
```
