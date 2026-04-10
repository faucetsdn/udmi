[**UDMI**](../../) / [**Docs**](../) / [**Messages**](./) / [System](#)

# Alarmset

# Alarmset Specification

A `alarmset` represents a set of alarms reporting telemetry data. This is the most common data, and should be stored in an appropriate time-series database.

Specific `alarm_names` within a `alarmset` should be specified in _snake_case_ and adhere to the
data ontology for the device (stipulated and verified outside of UDMI, e.g. [Digital Buildings Ontology](https://github.com/google/digitalbuildings/tree/master/ontology)).


Alarmset is represented in four locations:
- [Metadata](#metadata)
- [Event](#event)
- [State](#state)
- [Config](#config)

## Metadata

- **Schema Definition:** [model_alarmset.json](../../schema/model_alarmset.json)
 ([_🧬View_](../../gencode/docs/metadata.html#alarmset))
- [Working `metadata` Example](../../tests/schemas/metadata/example.json)

The `metadata.alarmset` subblock represents the abstract system expectation for what the device
_should_ be doing, and how it _should_ be configured and operated. This block specifies the
expected alarms that a device holds, along with, if the field is numeric, the expected units of those alarms.
The general structure of a `alarmset` block exists inside of a complete metadata message

* `alarmset`: Top level block designator.
  * `alarms`: Collection of alarm names.
    * _{`alarm_name`}_: Alarm name.
      * `units`: Expected units designation for this alarm.

## Event

- **Schema Definition:** [events_alarmset.json](../../schema/events_alarmset.json)
 ([_🧬View_](../../gencode/docs/events_alarmset.html#alarms))
- [Working `events_alarmset` Example](../../tests/schemas/events_alarmset/example.json)

A basic `alarmset` event message contains
the alarm data sent from a device. The message contains just the top-level `alarms` designator,
while the `alarmset` typing is applied as part of the [message envelope](envelope.md).

* `alarms`: Collection of alarm names.
  * _{`alarm_name`}_: Alarm name.
    * `present_value`: The specific alarm data reading. If the value represents a number, then this must be serialized as a JSON number, not a quoted string.
* `partial_update`: Optional indicator if this is an incremental update (not all alarms included).

Event telemetry messages should be sent "as needed" or according to specific requirements as
stipulated in the `config` block. The basic `alarmset` event message for a device should
contain the values for all representative alarms from that device, as determined by the associated
`config` block. If no alarms are specified in the `config` block, the device programming determines
the representative alarms.

### Incremental Updates and CoV

Incremental updates (e.g. for COV) can send only the specific updated alarms as an optimization,
while setting the top-level
[🧬`partial_update`](../../gencode/docs/events_alarmset.html#partial_update) field to `true` These
messages may be indiscriminately dropped by the backend systems, so a periodic full-update must
still be sent (as per `sample_rate_sec` below). Sending an update where all expected alarms are not
included, without this flag, is considered a validation error.

## State

- **Schema Definition:** [state_alarmset.json](../../schema/state_alarmset.json)
 ([_🧬View interactive_](../../gencode/docs/state.html#alarmset))
- [Working `state` Example](../../tests/schemas/state/example.json)

The [state](state.md) message from a device contains a `alarmset` block with the following
structure:

* `alarmset`: Top level block designator.
  * `alarms`: Collection of alarm names.
    * _{`alarm_name`}_: Alarm name.
      * (`status`): Optional [status](status.md) information about this alarm.
      * (`value_state`): Optional enumeration indicating the
        [state of the alarms value.](../specs/sequences/writeback.md#value_state)

In all cases, the alarms `status` field can be used to supply more information (e.g., the
reason for an _invalid_ or _failure_ `value_state`).

## Config

- [🧬Schema](../../gencode/docs/config.html#alarmset)
- [Working `config` Example](../../tests/schemas/config/writeback.json)

The [config](config.md) message for a device contains a `alarmset`
block with the following structure:e

* `alarmset`: Top level block designator.
  * `sample_rate_sec`: Maximum time between samples for the device to send out a _complete_
  update. It can send out updates more frequently than this.
  * `sample_limit_sec`: Minimum time between sample updates for the device (including complete
  and COV updates). Updates more frequent than this should be coalesced into one update.
  * `alarms`: Collection of alarm names, defining the representative alarm set for this device.
    * _{`alarm_name`}_: Alarm name.
      * `units`: Set as-operating units for this alarm.
      * (`set_value`): Optional setting to control the specified device alarm. If the set_value represents a number, then this must be serialized as a JSON number, not a quoted string. See [writeback documentation](../specs/sequences/writeback.md).
      * (`cov_threshold`): Optional threshold for triggering a COV event update.

The alarms defined in the `config.alarmset.alarms` dictionary is the authoritative source
indicating the representative alarms for the device (in both `event` and `state` messages). If
the device has additional alarms that are _not_ stipulated in the config they should be silently
dropped. If the device does not know about a stipulated alarm then it should report it as a
alarm with an _error_ level `status` entry in its `state` message, and exclude it from the event message.
If a `config` block is not present, or does not contain a `alarmset.alarms` object,
then the device should determine on its own which alarms to report.

If `sample_rate_sec` is not defined (or zero), then the system is expected to send an update at least every
300 seconds (5 minutes as a default value). A negative value would mean "don't send updates."
