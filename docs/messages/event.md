[**UDMI**](../../) / [**Docs**](../) / [**Messages**](./) / [Events](#)

# Events

Events can be one of:
- [Pointset (telemetry)](pointset.md#telemetry) ([_🧬schema_](../../gencode/docs/event_pointset.html))
- [System (logging, etc)](system.md#event) ([_🧬schema_](../../gencode/docs/event_system.html))
- [Discovery](../specs/discovery.md) ([_🧬schema_](../../gencode/docs/event_discovery.html))

Events are sent to the `events/<TYPE>` MQTT topic, e.g. `events/pointset` for pointset/telemetry updates

<!--example:state/example.json-->
```json
{
  "version": "1.3.14",
  "timestamp": "2018-08-26T21:39:29.364Z",
  "system": {
    "hardware": {
      "make": "ACME",
      "model": "Bird Trap"
    },
    "software": {
      "firmware": "3.2a"
    },
    "serial_no": "182732142",
    "last_config": "2018-08-26T21:49:29.364Z",
    "operational": true,
    "status": {
      "message": "Tickity Boo",
      "category": "system.config.apply",
      "timestamp": "2018-08-26T21:39:30.364Z",
      "level": 600
    }
  },
  "pointset": {
    "status": {  // Status scoped to overall pointset operation
      "message": "Invalid sample time",
      "category": "pointset.point.invalid",
      "timestamp": "2018-08-26T21:39:28.364Z",
      "level": 800
    },
    "points": {
      "return_air_temperature_sensor": {
        "status": {  // Status scoped to a specific point in a pointset
          "message": "Point return_air_temperature_sensor unable to read value",
          "category": "pointset.point.failure",
          "timestamp": "2018-08-26T21:39:28.364Z",
          "level": 800
        }
      },
      "nexus_sensor": {
      }
    }
  }
}
```
