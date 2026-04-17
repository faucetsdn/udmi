[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Modbus](#)

# Modbus RTU Specification

Before implementing automatic data source and point creation for Modbus RTU, the UDMI specification must define the required information. This document outlines the Modbus integration for UDMI, ensuring it is industry compliant while addressing UDMI's unique mapping and topology needs.

## Serial Bus Information

* Minimum required communication settings (per serial bus):
  * `baud_rate`
  * `data_bits`
  * `stop_bits`
  * `parity`
* Multiple devices can share the same serial bus.
* An IoT Gateway device can have multiple serial bus connections.
  * The communication settings are not unique identifiers. Multiple connections can have the same serial communication settings.
  * The Modbus ID is only unique within the scope of a single serial bus. Multiple proxied devices can have the same Modbus ID.

## Proxied Modbus Device Information

* Modbus ID
* Connected to which serial bus

## Modbus Point Information

* Minimum required information:
  * `range`
  * `data_type`
  * `offset`
  * `bit` (for some `data_type` values)
  * `length` (for some `data_type` values)

### Range Values

| Value |
| ----- |
| **coil_status** |
| **input_status** |
| **input_register** |
| **holding_register** |

### Data Type Values

| Value | Description |
| ----- | ----- |
| **binary** | Binary |
| **2_byte_unsigned** | 2 byte unsigned integer |
| **2_byte_unsigned_swapped** | 2 byte unsigned integer swapped |
| **2_byte_signed** | 2 byte signed integer |
| **2_byte_signed_swapped** | 2 byte signed integer swapped |
| **2_byte_bcd** | 2 byte BCD |
| **1_byte_lower** | 1 byte lower |
| **1_byte_upper** | 1 byte upper |
| **4_byte_unsigned** | 4 byte unsigned integer |
| **4_byte_signed** | 4 byte signed integer |
| **4_byte_unsigned_swapped** | 4 byte unsigned integer swapped |
| **4_byte_signed_swapped** | 4 byte signed integer swapped |
| **4_byte_unsigned_swapped_swapped** | 4 byte unsigned integer swapped bytes and words |
| **4_byte_signed_swapped_swapped** | 4 byte signed integer swapped bytes and words |
| **4_byte_float** | 4 byte float |
| **4_byte_float_swapped** | 4 byte float swapped |
| **4_byte_bcd** | 4 byte BCD |
| **4_byte_bcd_swapped** | 4 byte BCD swapped |
| **4_byte_mod10k** | 4 byte Mod10k |
| **4_byte_mod10k_swapped** | 4 byte Mod10k swapped |
| **6_byte_mod10k** | 6 byte Mod10k |
| **6_byte_mod10k_swapped** | 6 byte Mod10k swapped |
| **8_byte_mod10k** | 8 byte Mod10k |
| **8_byte_mod10k_swapped** | 8 byte Mod10k swapped |
| **8_byte_unsigned** | 8 byte unsigned integer |
| **8_byte_signed** | 8 byte signed integer |
| **8_byte_unsigned_swapped** | 8 byte unsigned integer swapped |
| **8_byte_signed_swapped** | 8 byte signed integer swapped |
| **8_byte_float** | 8 byte float |
| **8_byte_float_swapped** | 8 byte float swapped |
| **char** | Fixed length string |
| **varchar** | Variable length string |

## Implementation Details

Serial bus information is stored in the IoT Gateway configuration under `metadata:localnet:families`:
* Includes an identifier for each serial bus that can be referenced by the Proxied Device.

Modbus device information is stored in the Proxied device configuration under `metadata:localnet:families`:
* Modbus ID
* Identifier for the serial bus

Modbus point information is stored in the point's `ref` field, in a URI format.

The unified format for both Modbus RTU and TCP:

**`modbus://[network]/<unitid>/<range>/<address>[/<quantity>][?interpretation]`**

For backward compatibility, the following legacy Modbus RTU format is also supported:

**`modbus://[modbus_id]/[range]/[data_type]/[offset]/[bit|length]`**

For legacy Modbus RTU, the final parameter (`bit` or `length`) is only required if the `data_type` provided requires the additional parameter. The `binary` `data_type` requires the `bit` parameter. The `char` and `varchar` `data_type` require the `length` property.
