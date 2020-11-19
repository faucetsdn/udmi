# Config & State Block Transactions

* When a `config` block is received, a `state` update should be generated with a corresponding last_update
* If additional processing is required, then the `reconciling` flag should be set `true`.
* `reconciling` should be returned to `false` (or absent) once the device is done updating.
* The device can asynchroniously update `state` if some other condition changes independent of `config`.

## Config Block

* `timestamp`: Server-side timestamp of when the config was generated.
* `system`: Sub-block for system-level information.
* ...: Other sub-blocks as defined in the standard (e.g. _pointset_ or _gateway_).

## State Block
* `system`:
  * `last_config`: Server-side timestamp from the last processed `config` block.
  * `reconciling`: Boolean indicating if the system is still processing the last `config` update.
