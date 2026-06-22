[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [BACnet](#)

THIS IS A PROVISIONAL SPEC THAT IS SUBJECT TO CHANGE.

# BACnet Specification

UDMI supports reading BACnet points by specifying them via a `bacnet://` URL schema.

## URI Schema

`bacnet://<device_id>/<object_type>:<object_instance>[#property_identifier]`

*   **`device_id`**: The BACnet Device Object Instance Number. It uniquely identifies the BACnet device on the network.
    *   Must be a positive integer in the range `1` to `4194303` (22-bit Device Object Instance range).
    *   Leading zeroes are invalid (e.g., `01293` is invalid), and `0` is invalid.
*   **`object_type`**: A 2 to 4 character uppercase string representing the BACnet object type (e.g., `AI`, `AO`, `AV`, `BI`, `BO`, `BV`, `MSI`, `MSO`, `MSV`, `DO`).
*   **`object_instance`**: A non-negative integer representing the BACnet object instance index (e.g., `0`, `1`, `21`, `100`).
    *   Leading zeroes are not allowed unless the index is `0`.
*   **`property_identifier`**: (Optional) A lowercase string with underscores representing the property identifier of the BACnet object, preceded by `#` (e.g., `#present_value`, `#something_else`).
    *   If omitted, it typically defaults to the `present_value` property.

### Object Types

The following are common BACnet object types used as part of the point reference:

*   **`AI`**: Analog Input
*   **`AO`**: Analog Output
*   **`AV`**: Analog Value
*   **`BI`**: Binary Input
*   **`BO`**: Binary Output
*   **`BV`**: Binary Value
*   **`MSI`**: Multi-state Input
*   **`MSO`**: Multi-state Output
*   **`MSV`**: Multi-state Value
*   **`DO`**: Device Object / Binary Output

### Network Parameters

Under the `localnet` configuration, BACnet parameters define the communication settings:

*   **`addr`**: The BACnet Device Object Instance Number. Must be a positive integer in the range `1` to `4194303` with no leading zeroes.
*   **`network`**: (Optional) The 16-bit BACnet Network Number. Must be a positive integer in the range `1` to `65534` with no leading zeroes.

## Examples

The metadata values in the examples below map to the following complete BACnet URIs:

*   **Room Temperature Sensor**: `bacnet://291842/AI:2#present_value`
*   **Fan Run Status**: `bacnet://3/DO:0`
*   **Discharge Air Temp**: `bacnet://1/AI:2`
*   **Damper Command**: `bacnet://291842/BO:21`

### Network Configuration Example

The BACnet network specification as part of a gateway `metadata.json` file under `localnet.families`:

```json
{
  "localnet": {
    "families": {
      "bacnet": {
        "addr": "19233",
        "network": "9288"
      }
    }
  }
}
```

Or defined under `localnet.networks` as a named BACnet network:

```json
{
  "localnet": {
    "networks": {
      "bacnet_1": {
        "family": "bacnet",
        "addr": "19233",
        "network": "9288"
      }
    }
  }
}
```

### Proxy Device Example

For a proxied BACnet device, the `gateway` block in the `metadata.json` specifies the protocol family, while the `pointset` block defines individual points and their object mappings.

```json
{
  "gateway": {
    "target": {
      "family": "bacnet"
    }
  },
  "localnet": {
    "families": {
      "bacnet": {
        "addr": "582312"
      }
    }
  },
  "pointset": {
    "points": {
      "filter_alarm_pressure_status": {
        "units": "No-units",
        "ref": "BV:11#present_value"
      },
      "filter_differential_pressure_sensor": {
        "units": "Degrees-Celsius",
        "ref": "AV:12#present_value"
      }
    }
  }
}
```
