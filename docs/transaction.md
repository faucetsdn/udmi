# Config & State Transactions

* The `state` and `config` messages work together to represent a transactional state between the cloud and device.
* When a `config` is received, a `state` update should be generated with a corresponding last_update.
* If additional processing is required, then the `updating` flag should be set `true`.
* `updating` should be returned to `false` (or absent) once the device is done updating.
* The device can asynchroniously update `state` if some other condition changes independent of `config`.

## Config Message

* `timestamp`: Server-side timestamp of when the config was generated.
* `system`: Subsystem for system-level information.
* ...: Other subsystems as defined in the standard (e.g. _pointset_ or _gateway_).

## State Message
* `system`:
  * `last_config`: Server-side timestamp from the last processed `config`.
  * `updating`: Boolean indicating if the system is still processing the last `config` update.
