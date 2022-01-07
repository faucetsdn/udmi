[**UDMI**](../../) / [**Docs**](../) / [**Messages**](./) / [Status](#)

# Status Objects

The State and system/logentry messages both have `status` and `logentries` sub-fields, respectively, that
follow the same structure.

- State `status` represents _sticky_ conditions that persist until the situation is cleared, e.g.
  “device disconnected”.
    - [🧬Pointset Status](../../gencode/docs/state.html#pointset_points_pattern1_status) 
    - [🧬System Statuses](../../gencode/docs/state.html#system_statuses)
- [🧬Logentry events](../../gencode/docs/event_system.html#logentries) are transitory event that
  happen, e.g. “connection failed”.

## Example
```json
{
...
"statuses": {
      "base_system": {
        "message": "Tickity Boo",
        "category": "device.state.com",
        "timestamp": "2018-08-26T21:39:30.364Z",
        "level": 600
      }
}
```
