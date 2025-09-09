[**UDMI**](../../) / [**Docs**](../) / [**Guides**](./) / [Define Presentation Attributes](#)

# Presentation Layer Configuration Guide

This guide explains how to use the embedded JSON schema attributes to control
what information is displayed in the UI and how it's organized.

The entire system is controlled by adding a special object named `$presentation`
to properties within our schema files. A script reads these special objects to
generate the final UI configuration.


**Try out an interactive demo at [🧪 Presentation Layer Demo](presentation_layer_demo.html)**


## The Core Example: A Simple Device

Let's start with a basic schema for a device. By default, nothing is visible.

`device_model.json`

```json
{
  "title": "Device Model",
  "type": "object",
  "properties": {
    "device_id": {
      "type": "string",
      "description": "A unique identifier for the device."
    },
    "last_seen": {
      "type": "string",
      "description": "The last time the device was online."
    }
  }
}
```

Right now, both `device_id` and `last_seen` are hidden.

### Step 1: Making a Property Visible (`display`)

To make a property appear, we add `$presentation` and set `"display": "show"`.
The system is opt-in, so you must explicitly show every property you want to
see.

```json
{
  //...
  "properties": {
    "device_id": {
      "type": "string",
      "description": "A unique identifier for the device.",
      "$presentation": {
        "display": "show"  // display this property
      }
    },
    "last_seen": {
      "type": "string",
      "description": "The last time the device was online."
    }
  }
  //...
}
```

**Result:**
* `device_id` is now visible in the UI.
* `last_seen` remains hidden.

### Step 2: Grouping Properties (`section`)

You can group related properties into sections. The `section` attribute is
hereditary - meaning child properties automatically belong to the same `section`
as their parent.

Let's add a `location` object and put it in a "Location Details" section.

```json
{
  //...
  "properties": {
    "device_id": {
      // ... as before
    },
    "location": {
      "type": "object",
      "$presentation": {
        "display": "show",
        "section": "Location Details"
      },
      "properties": {
        "floor": {
          "type": "number",
          "$presentation": { "display": "show" }
        },
        "room": {
          "type": "string",
          "$presentation": { "display": "show" }
        }
      }
    }
  }
  //...
}
```

**Result:**
* A new section "Location Details" is created.
* The `location` object appears in this section. 
* `location.floor` and `location.room` automatically inherit the "Location
  Details" section from their parent.

### Step 3: Hiding a Group (`display: hide`)

Setting `"display": "hide"` on a parent property will hide it and all its
children, no matter what their own settings are. This is useful for hiding
entire blocks of diagnostic data.

Let's add a diagnostics object that we don't want to show.

```json
{
  //...
  "properties": {
    "device_id": { /* ... */ },
    "location": { /* ... */ },
    "diagnostics": {
      "type": "object",
      "$presentation": {
        "display": "hide"
      },
      "properties": {
        "last_error_code": {
          "type": "string",
          // This 'show' will be IGNORED because the parent is hidden.
          "$presentation": { "display": "show" }
        }
      }
    }
  }
  //...
}
```

**Result:**
* The entire diagnostics object is hidden.
* `diagnostics.last_error_code` is also hidden, because the parent's "hide"
  setting overrides the child's "show".

### Advanced: Presenting Dynamic Maps (`patternProperties`)

Sometimes, we have objects where the keys are not fixed, like a map of software
versions. We need to tell the system which specific keys from the map we want to
display. This is done with `$presentation.paths`.

#### Use Case 1: Simple Maps

This is for maps where the value is a simple type (like a string). 
Let's add a `software_versions` map to our device.

```json
{
  //...
  "properties": {
    // ... other properties
    "software_versions": {
      "type": "object",
      "description": "A map of software component versions.",
      "patternProperties": {
        "^[a-z_]+$": { "type": "string" }
      },
      "$presentation": {
        "paths": {
          "firmware": { "style": "bold" },
          "operating_system": { "style": "bold" }
        }
      }
    }
  }
  //...
}
```

*How it works:*

* The paths object lists the dynamic keys we want to "pin" and
  display (`firmware`, `operating_system`).
* The value for each key (`{ "style": "bold" }`) is a presentation object that
  gets applied.

**Result:**

* This creates two properties in the UI: `software_versions.firmware`
  and `software_versions.operating_system`.

#### Use Case 2: Complex Maps with Configuration Injection

This is the most advanced case. It's used when a map's value is another complex
object, and you want to configure that child object differently depending on the
parent key.

Let's add `network_protocols` to our device. We'll have different settings
for `bacnet` and `modbus`. This requires two files.

* *File 1:* `device_model.json` (The Parent)

  Here, the `$presentation.paths` for `bacnet` and `modbus` contain an extra
  object that will be "injected" into the child schema. Note
  the `settings.paths` key - this is the convention.
    ```json
    {
      // In device_model.json
      //...
      "properties": {
        // ... other properties
        "network_protocols": {
          "type": "object",
          "patternProperties": {
            "^[a-z]+$": { "$ref": "file:protocol_details.json" }
          },
          "$presentation": {
            "paths": {
              "bacnet": {
                "settings.paths": {
                  "device_instance": { "description": "BACnet Device Instance ID" }
                }
              },
              "modbus": {
                "settings.paths": {
                  "serial_port": { "description": "Serial port for Modbus RTU" },
                  "baud_rate": { }
                }
              }
            }
          }
        }
      }
      //...
    }
    ```

* *File 2*: `protocol_details.json` (The Generic Child)

  This schema defines a generic `settings` map. It has no
  special `$presentation` block; it's designed to be configured by its parent.

    ```json
    {
      "title": "Protocol Details",
      "type": "object",
      "properties": {
        "settings": {
          "type": "object",
          "patternProperties": {
            "^[a-z_]+$": { 
              "type": "string" 
            }
          }
        }
      }
    }
    ```

*How it works:*

* The parser processes `network_protocols.bacnet`. It sees the `settings.paths`
object and holds onto it as an "injected configuration".
* It then steps into `protocol_details.json` to process it.
* When it gets to the `settings` property, it sees the injected configuration
  waiting for it.
* It uses the injected keys (`device_instance`) to build the final properties.

**Result:**

* It generates `network_protocols.bacnet.settings.device_instance`
  and `network_protocols.modbus.settings.serial_port`, each with their own
  specific details, all from one generic child schema.

---

## Adding a New Root Schema

A "root schema" is a top-level file that the script starts parsing from. You must explicitly tell the script which files are roots.

This list is managed in 
[docs/guides/presentation_roots.md](presentation_roots.md).

To add a new root schema (e.g., `my_new_model.json`):

* Make sure `my_new_model.json` exists in the `schema/` folder. 
* Open [docs/guides/presentation_roots.md](presentation_roots.md). 
* Find the JSON code block inside the file. 
* Add your filename to the "roots" array.

**Before:**

```json
{
  "roots": [
    "device_model.json"
  ]
}
```

**After:**

```json
{
  "roots": [
    "device_model.json",
    "my_new_model.json"
  ]
}
```

The script will now parse both files when it runs.

