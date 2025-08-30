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


2. **Overriding the Default for Specific Properties**:
   You can override the $defaultPresentation on any property using
   the `$presentation` key in one of several ways:

    - **To change the section**: `"$presentation": "new_section_name"`

    - **To provide a custom label** (while inheriting the default section): `"$presentation": { "label": "Your Custom Label" }`

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


3. **Path-Based Defaults**:
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


4. **Handling Simple Map Properties with default keys**:
   For cases where a property is a simple map (like the `model_system.software`
   property is a `Map<String, String>`) and certain keys need to be presented
   for this property, the `presentationProperties` keyword can be used.
   
   Example (`schema/model_system.json`):

   ```json
   {
      "$defaultPresentation": "system",
      "properties": {
         "software": {
            "$presentation": {
               "presentationProperties": {
                  "firmware": {},
                  "os": {},
                  "driver": {}
               }
            }
         }
      }
   }
   ```
   
   *Result*: `firmware`, `os` and `driver` keys are added as keys to the `model_system.software` property.

   ```json
   {
      "system": {
         "model_system.software.firmware": {
            "label": "software.firmware",
            "expectedType": "string"
         },
         "model_system.software.os": {
            "label": "software.os",
            "expectedType": "string"
         },
         "model_system.software.driver": {
            "label": "software.driver",
            "expectedType": "string"
         }
      }
   }
   ```


5. **Handling Complex Context-Aware Map Properties**:
   For the most complex cases where a property is a generic map and the 
   schema's presentation depends on its specific use (like the `adjunct` 
   property), the logic is defined using the nesting of keywords 
   `$presentation`, `paths` and `presentationProperties` in the leaf node.

   Example: 

   - Define parent keys in `schema/model_localnet.json`:

   ```json
   {
      "properties": {
         "families": {
            "$presentation": {
               "presentationProperties": {
                  "bacnet": { "$ref": "file:model_localnet_family.json" },
                  "modbus": { "$ref": "file:model_localnet_family.json" },
                  "ether":  { "$ref": "file:model_localnet_family.json" }
               }
            }
         }
      }
   }
   ```
   
   - Define specific presentation in leaf node in `schema/model_localnet_family.json`
   
   ```json
   {
      "properties": {
         "addr": {
            "$presentation": {
               "paths": {
                  "model_localnet.families.*": {
                     "section": "localnet"
                  }
               }
            }
         },
         "adjunct": {
            "type": "object",
            "existingJavaType": "java.util.Map<String, String>",
            "$presentation": {
               "paths": {
                  "model_localnet.families.bacnet.adjunct": {
                     "section": "localnet",
                     "presentationProperties": {
                        "name": { }
                     }
                  },
                  "model_localnet.families.modbus.adjunct": {
                     "section": "localnet",
                     "presentationProperties": {
                        "serial_port": { }
                     }
                  }
               }
            }
         }
      }   
   }
   ```
   *Result*: `bacnet`, `modbus` and `ether` will be added as specific keys
   under `model_localnet.families`. `addr` property will be added for all 3
   families, while `name` will only be added for `bacnet`, and `serial_port` 
   will only be added for `modbus`.

   ```json
   {
      "localnet": {
         "model_localnet.families.bacnet.addr": {
            "label": "model_localnet.families.bacnet.addr",
            "type": "string"
         },
         "model_localnet.families.bacnet.name": {
            "label": "model_localnet.families.bacnet.name",
            "type": "string"
         },
         "model_localnet.families.modbus.addr": {
            "label": "model_localnet.families.modbus.addr",
            "type": "string"
         },
         "model_localnet.families.modbus.serial_port": {
            "label": "model_localnet.families.modbus.serial_port",
            "type": "string"
         },
         "model_localnet.families.ether.addr": {
            "label": "model_localnet.families.ether.addr",
            "type": "string"
         }
      }
   }
   ```
