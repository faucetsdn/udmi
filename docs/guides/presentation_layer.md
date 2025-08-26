[**UDMI**](../../) / [**Docs**](../) / [**Guides**](./) / [Define Presentation Attributes](#)

# A User's Guide to the Presentation Layer

This guide explains how to use special keys within your JSON schemas to control
how properties are displayed in a user interface. A
script `bin/generate_presentation_config.py` reads these keys to automatically
generate a UI configuration `gencode/presentation/presentation.json`.

## Core Concepts

There are two primary keys you will use:

- `$defaultPresentation`: A top-level key in a schema file that sets the default
  presentation rules for all properties within that file.
- `$presentation`: A key placed inside a specific property to give it custom
  rules or to override the default.

---

## Configuration Methods

Here are the different ways to configure your schemas, from simplest to most
advanced.

1. **Simple File-Wide Default**:
   To place all properties from a single file into the same UI section,
   add `$defaultPresentation` with the section name to the top of the file. The
   property's name will be used as its label by default.

   Example (`schema/model_system.json`):
   ```json
    {
        "$defaultPresentation": "system",
        "properties": {
            "name": { "type": "string" },
            "serial_no": { "type": "string" }
        }
    }
    ```
   *Result*: Both properties `name` and `serial_no` will appear in the "system"
   section in the output `gencode/presentation/presentation.json`.
---

2. **Overriding the Default for Specific Properties**:
   You can override the $defaultPresentation on any property using
   the `$presentation` key in one of several ways:

    - **To change the section**: `"$presentation": "new_section_name"`

    - **To provide a custom label** (while inheriting the default section): "$presentation": { "label": "Your Custom Label" }`

    - **To exclude a property from the UI**: `"$presentation": {}`

    Example:
    ```json
    {
        "$defaultPresentation": "system",
        "properties": {
            "name": {
                "type": "string",
                "$presentation": { "label": "Device Name" }
            },
            "hardware_id": {
                "type": "string",
                "$presentation": {}
            }
        }
    }
    ```
   *Result*: `name` appears in the "system" section with the label "Device Name," and `hardware_id` is omitted from the presentation config.
---

3. **Advanced Path-Based Defaults**:
   For schemas used in multiple contexts (like `schema/configuration_endpoint.json`), you can make the `$defaultPresentation` apply rules based on a property's path.

   Example (`schema/configuration_endpoint.json`):
   ```json
    {
        "$defaultPresentation": {
            "paths": {
                "configuration_execution.*": {
                "section": "cloud_iot_config"
                }
            }
        },
        "properties": {
            "hostname": { "type": "string" },
            "port": { "type": "integer" }
        }
    }
    ```
   *Result*: When this schema is approached from parent `configuration_execution`, both `hostname` and `port` will be placed in the `cloud_iot_config` section.
---

4. **Handling Complex Context-Aware Properties**:
   For the most complex cases where a generic schema's presentation depends on
   its specific use (like the `adjunct` property), the logic should be defined
   in the parent schema.

   The parent schema acts as an orchestrator, layering specific presentation
   rules on top of the generic child schema it references. This is done by
   defining `$presentation` and `presentationProperties` within the parent's
   definition of the relevant property.

   Example (`schema/model_localnet.json` orchestrating `adjunct` properties):

    ```json
    {
      "$defaultPresentation": "localnet",
      "families": {
        "$presentation": {
          "presentationProperties": {
            "bacnet": {
              "$ref": "file:model_localnet_family.json",
              "properties": {
                "adjunct": {
                  "$presentation": {
                    "presentationProperties": {
                      "name": {},
                      "description": {}
                    }
                  }
                }
              }
            },
            "modbus": {
              "$ref": "file:model_localnet_family.json",
              "properties": {
                "adjunct": {
                  "$presentation": {
                    "presentationProperties": {
                      "serial_port": {}
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    ```
    
    *Result*: `bacnet` and `modbus` will be added as specific keys
    under `model_localnet.families`, The `name` property will be presented in
    the `adjunct` property when the family is `bacnet`, and `serial_port` will be
    presented when the family is `modbus`, correctly handling the context.
    
    ```json
    {
      "localnet": {
        "model_localnet.families.bacnet.name": {
          "label": "model_localnet.families.bacnet.name",
          "type": "string"
        },
        "model_localnet.families.bacnet.description": {
          "label": "model_localnet.families.bacnet.description",
          "type": "string"
        },
        "model_localnet.families.modbus.serial_port": {
          "label": "model_localnet.families.modbus.serial_port",
          "type": "string"
        }
      }
    }
    ```

---