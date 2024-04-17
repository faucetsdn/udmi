[**UDMI**](../../) / [**Docs**](../) / [**Messages**](./) / [Events](#)

# Events

Events can be one of:
- [Pointset (telemetry)](pointset.md#telemetry) ([_🧬schema_](../../gencode/docs/events_pointset.html))
- [System (logging, etc)](system.md#event) ([_🧬schema_](../../gencode/docs/events_system.html))
- [Discovery](../specs/discovery.md) ([_🧬schema_](../../gencode/docs/events_discovery.html))

Events are sent to the `events/<TYPE>` MQTT topic, e.g. `events/pointset` for pointset/telemetry updates
