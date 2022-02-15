[**UDMI**](../../../) / [**Docs**](../../) / [**Specs**](../) / [**Sequences**](./) / [Config](#)

# Config & State Sequence

* The [`state`](../../messages/state.md) and [`config`](../../messages/config.md) messages work together to represent a transactional state between the cloud and device.
* When any `config` is received, a `state` update should be generated with a corresponding last_update.
* The state message should be sent within 5 seconds
  * If additional processing is required, then the `updating` flag should be set `true`.
  * `updating` should be returned to `false` (or absent) once the device is done updating.
* The device can asynchronously update `state` if some other condition changes independent of
  `config` message, including when:
  * There is an update from an external source, e.g. a BMS or local controller
  * There is an update from internal logic 
* Other [sequences](./) such as [writeback](writeback.md) may have specific behaviors relating to
  state messages 

![State and config](images/state.png)

Generated using <https://sequencediagram.org>
```
participant Device
participant Broker
participantspacing 5
Broker->Device: **CONFIG**
Device->Broker: **STATE**
[->Device:Update from external\nsource, e.g. BMS 
Device->Broker: **STATE**
[->Device:Change
Device->Broker: **STATE**
```

## Config Message

* `timestamp`: Server-side timestamp of when the config was generated.
* `system`: Subsystem for system-level information.
* ...: Other subsystems as defined in the standard (e.g. _pointset_ or _gateway_).

## State Message

* `system`:
  * `last_config`: Server-side timestamp from the last processed `config`.
  * `updating`: Boolean indicating if the system is still processing the last `config` update.
  