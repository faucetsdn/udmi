[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Modbus](#)

# Modbus Specification

UDMI supports reading Modbus pointsets by specifying them via a `modbus://` URL schema.

## URI Schema

`modbus://<host>[:port]/<unitid>/<function>/<address>[/<quantity>][?interpretation_parameters]`

*   **host**: The host name or IP address of the Modbus endpoint. This maps to a named network, as indicated in the `networks` field in `model_localnet.json`.
*   **port**: (Optional) The TCP port.
*   **unitid**: The Slave ID (Unit Identifier).
*   **function**: The Function Code (e.g., 3 for Holding Registers).
*   **address**: The starting register address.
*   **quantity**: (Optional) The number of registers to fetch.
*   **interpretation_parameters**: (Optional) Query string properties that are necessary to properly interpret data fetched from a register.

## Network Parameters

The `host` maps to a named network in the device's `model_localnet.json` (under the `networks` field). Each named network can define the following parameters for communication:

*   **baud**: The baud rate.
*   **protocol**: e.g., RTU, TCP.
*   **parity**: For serial RTU.
*   **data bits**: For serial RTU.
*   **stop bits**: For serial RTU.

## Interpretation Parameters

Parameters passed in the query string define how to interpret the fetched register data:

*   **byte order**: e.g., Big-Endian (MSB first) or Little-Endian (LSB first).
*   **data type**: e.g., INT16, UINT32, BOOLEAN, ASCII.
*   **word order**: (For 32-bit values) e.g., High-Word First or Low-Word First.
*   **multiplier/divider**: (Scale factor) e.g., 1.0, 0.01, 100.0.
