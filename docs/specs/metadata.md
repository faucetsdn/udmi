[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Metadata](#)

# Metadata Description File

A device's `metadata.json` is a description about the device: a specification
about how the device should be configured and expectations about what the device
should be doing. It is used to provide the basic information for registering
devices in a IoT Core registry, generation of other template files (e.g.
_config_ block) associated with the device, and as a means for runtime
validation of what the device is actually sending.

The `metadata.json` files are stored in a `devices/{device_id}` directory
with an expected 1:1 mapping between device directory and device entry
in a IoT Core registry.

## System Tooling

There are three main sets of tools that rely on the `metadata.json` file,
all found in the UDMI `bin/` directory.
* [`genkeys`](../tools/keygen.md): Generates public/private device key pairs (optional as needed).
* [`registrar`](../tools/registrar.md): Validates metadata files and can (optionally) register devices.
* [`validator`](../tools/validator.md): Validates live data stream from devices.

## Output Files

The various tools will generate some output files in the same `devices/` directory:
* `rsa_*`: Device public/private key files (`genkeys` tool).
* `metadata_norm.json`: Normalized version of the `metadata.json` file (`registrar` tool).
* `generated_config.json`: Default device IoT Core `config` block (`registrar` tool).
* `errors.json`: Detailed error file (`registrar` tool).

## Metadata Structure

The structure of the metadata file is shown in the [🧬metadata schema](../../gencode/docs/metadata.html)

## Metadata Registration and Validation

Using UDMI on a project entails not only the base device implementations, but also
properly registering and validating device configuration. The [registrar](../tools/registrar.md)
tool and [validator](../tools/validator.md) tool provide a means to configure and check site
installations, respectively.

## Example

```json
{
  "version": 1,
  "timestamp": "2018-08-26T21:39:29.364Z",
  "description": "Generic test example metadata file",
  "system": {
    "location": {
      "site": "US-SFO-XYY",
      "section": "NW-2F",
      "position": {
        "x": 10,
        "y": 20
      }
    },
    "physical_tag": {
      "asset": {
        "guid": "bim://04aEp5ymD_$u5IxhJN2aGi",
        "site": "US-SFO-XYY",
        "name": "AHU-1"
      }
    },
    "aux": {
      "suffix": "extention11-optional"
    }
  },
  "cloud": {
    "auth_type": "ES256"
  },
  "pointset": {
    "points": {
      "return_air_temperature_sensor": {
        "units": "Degrees-Celsius",
        "baseline_value": 20,
        "baseline_tolerance": 2
      },
      "room_setpoint": {
        "writeable": true,
        "units": "Degrees-Celsius",
        "baseline_value": 20,
        "baseline_state": "applied"
      }
    }
  },
  "testing": {
    "targets": {
      "invalid": {
        "target_point": "return_air_temperature_sensor",
        "target_value": -20
      },
      "failure": {
        "target_point": "room_setpoint",
        "target_value": -20
      }
    }
  }
}
```