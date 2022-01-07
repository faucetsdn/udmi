[**UDMI**](../../) / [**Docs**](../) / [**Messages**](./) / [State](#)

# State Message

**Schema Definition:** [state.json](../../schema/state.json)
 ([_🧬View_](../../gencode/docs/state.html))

* There is an implicit minimum update interval of _one second_ applied to state updates, and it
is considered an error to update device state more often than that. If there are multiple
_state_ updates from a device in under a second they should be coalesced into one update
(sent after an appropriate backoff timer) and not buffered (sending multiple messages).
* `last_config` should be the timestamp _from_ the `timestamp` field of the last successfully
parsed `config` message (not the timestamp the message was received/processed).
* The state message are sent as a part of [sequences](../specs/sequences/)

## Example

```json
{
  "version": 1,
  "timestamp": "2018-08-26T21:39:29.364Z",
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
  "pointset": {
    "points": {
      "return_air_temperature_sensor": {
        "status": {
          "message": "Invalid sample time",
          "category": "device.config.validate",
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