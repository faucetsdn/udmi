[**UDMI**](../../) / [**Docs**](../) / [**Messages**](./) / [System](#)

# System Block

Primarily used for things like logging, general status, firmware management, etc...

* Defined by [<em>system.json</em>](../../schema/state_system.json)
* [example](../../tests/state.tests/example.json)

## State

**Schema Definition:** [state_system.json](../../schema/state_system.json)
 ([_🧬View_](../../gencode/docs/state.html#system))

### Example
```json
{
  ...
  "system": {
    "make_model": "ACME Bird Trap",
    "firmware": {
      "version": "3.2a"
    },
    "serial_no": "182732142",
    "last_config": "2018-08-26T21:49:29.364Z",
    "operational": true,
    "statuses": {
      "base_system": {
        "message": "Tickity Boo",
        "category": "device.state.com",
        "timestamp": "2018-08-26T21:39:30.364Z",
        "level": 600
      }
    }
  },
```

## Event

**Schema Definition:** [event_system.json](../../schema/event_system.json)
 ([_🧬View_](../../gencode/docs/event_system.html))

### Example
```json
{
  ...
  "logentries": [
    {
      "message": "things are happening",
      "detail": "someplace, sometime",
      "timestamp": "2018-08-26T21:39:19.364Z",
      "category": "com.testCategory",
      "level": 600
    },
    {
      "message": "something else happened",
      "timestamp": "2018-08-26T21:39:39.364Z",
      "detail": "someplace, sometime",
      "category": "com.testCategory",
      "level": 700
    }
  ]
}

```

## Metadata

**Schema Definition:** [metadata_system.json](../../schema/metadata_system.json)
 ([_🧬View_](../../gencode/docs/metadata.html#system))

### Example
```JSON
{
  "system": {
    "location": {
      "site": "US-SFO-XYY",
      "section": "NW-2F",
      "position": {
        "x": 10,
        "y": 20
      }
    },
}
```