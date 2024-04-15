[**UDMI**](../../) / [**Docs**](../) / [**Messages**](./) / [Status](#)

# Status Objects

The State and system/logentry messages both have `status` and `logentries` sub-fields, respectively, that
follow the same structure.

- State `status` represents _sticky_ conditions that persist until the situation is cleared, e.g.
  “device disconnected”.
    - [🧬Pointset Status](../../gencode/docs/state.html#pointset_points_pattern1_status) 
    - [🧬System Status](../../gencode/docs/state.html#system_status)
- [🧬Logentry events](../../gencode/docs/events_system.html#logentries) are transitory event that
  happen, e.g. “connection failed”.

## Example

The working examples below demonstrate `status` and `logentries` fields in different message types:
- `state`(../../tests/schemas/state/example.json)
- `events_system`(../../tests/schemas/events_system/example.json)
