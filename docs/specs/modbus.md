[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Modbus](#)

# Modbus Specification

UDMI supports reading Modbus points by specifying them via a `modbus://` URL schema.

## URI Schema

`modbus://<host>[:port]/<unitid>/<function>/<address>[/<quantity>][?interpretation]`

*   **`host`**: The host name or IP address of the Modbus endpoint.
    *   If the `host` maps to a named network, as indicated in the `networks` field in the `gateway` model, then the parameters there define the effective parameters to use.
*   **`port`**: (Optional) The TCP port.
*   **`unitid`**: The Slave ID (Unit Identifier).
*   **`function`**: The Function Code (see [Function Codes](#function-codes)).
*   **`address`**: The starting register address.
*   **`quantity`**: (Optional) The number of registers to fetch.
*   **`interpretation`**: (Optional) Query string properties that are necessary to properly interpret data fetched from a register.

### Function Codes

*   **`1`**: Read Coils
*   **`2`**: Read Discrete Inputs
*   **`3`**: Read Holding Registers
*   **`4`**: Read Input Registers
*   **`5`**: Write Single Coil
*   **`6`**: Write Single Register
*   **`15`**: Write Multiple Coils
*   **`16`**: Write Multiple Registers

### Interpretation Parameters

Parameters passed in the query string define how to interpret the fetched register data:

*   **`border`**: i.e., `MSB` (`Big-Endian`) or `LSB` (`Little-Endian`).
*   **`type`**: i.e., `INT16`, `UINT32`, `BOOLEAN`, `ASCII`.
*   **`worder`**: i.e., `HWF` (`High-Word First`) or `LWF` (`Low-Word First`) (for 32-bit values).
*   **`scale`**: i.e., `1.0`, `0.01`, `100.0` (scale factor).

### Network Parameters

The `host` maps to a named network in the device's `model_localnet.json` (under the `networks` field). Each named network can define the following parameters for communication:

*   **`baud`**: The baud rate.
*   **`protocol`**: i.e., `RTU`, `TCP`.
*   **`parity`**: For serial `RTU`.
*   **`data bits`**: For serial `RTU`.
*   **`stop bits`**: For serial `RTU`.

## Examples

The metadata values in the examples below map to the following complete Modbus URIs:

*   **Network RTU Point**: `modbus://modbus_rtu_1/1/3/40001/1?type=INT16&border=MSB`
*   **`fan_status`**: `modbus://modbus_rtu_1/2/1/101?type=BOOLEAN`
*   **`filter_differential_pressure`**: `modbus://modbus_rtu_1/2/4/30005?type=UINT32&worder=LWF&scale=0.01`

### Network Configuration Example

The serial-bus `RTU` network specification as part of a gateway `metadata.json` file:

```json
{
  "localnet": {
    "networks": {
      "modbus_rtu_1": {
        "family": "modbus",
        "adjunct": {
          "protocol": "RTU",
          "baud": 9600,
          "parity": "none",
          "data_bits": 8,
          "stop_bits": 1,
          "device": "COM1"
        }
      }
    }
  }
}
```

### Proxy Device Example

For a proxied Modbus device, the `gateway` block in the `metadata.json` specifies the target device ID (Unit ID), while the `pointset` block defines the individual points and their register mappings.

```json
{
  "gateway": {
    "target": {
      "family": "modbus",
      "addr": "2",
      "network_id": "modbus_rtu_1"
    }
  },
  "pointset": {
    "points": {
      "fan_status": {
        "ref": "1/101?type=BOOLEAN"
      },
      "filter_differential_pressure": {
        "units": "Pascals",
        "ref": "4/30005?type=UINT32&worder=LWF&scale=0.01"
      }
    }
  }
}
```


