[**UDMI**](../../) / [**Docs**](../) / [**Messages**](./) / [System](#)

# Alarmset

# Alarmset Specification

An `alarmset` represents a set of alarms reporting state change events.

Specific `alarm_names` within an `alarmset` should be specified in _snake_case_ and adhere to the
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
expected alarms that a device holds.
The general structure of an `alarmset` block exists inside of a complete metadata message

* `alarmset`: Top level block designator.
  * `alarms`: Collection of alarm names.
    * _{`alarm_name`}_: Alarm name.

## Event

- **Schema Definition:** [events_alarmset.json](../../schema/events_alarmset.json)
 ([_🧬View_](../../gencode/docs/events_alarmset.html#alarms))
- [Working `events_alarmset` Example](../../tests/schemas/events_alarmset/example.json)

A basic `alarmset` event message contains the alarm data sent from a device. The message contains
just the top-level `alarms` designator, while the `alarmset` typing is applied as part of the
[message envelope](envelope.md).

* `alarms`: Collection of alarm names.
  * _{`alarm_name`}_: Alarm name.
    * `active`: Boolean indicating whether or not the alarm is currently active.
    * `activate_time`: The timestamp of when the alarm most recently became active.
    * `activate_ack`: Optional details about acknowledgement of the alarm activation.
    * `return_to_normal_time`: Optional timestamp of when the alarm deactivated.
    * `return_to_normal_ack`: Optional details about acknowledgement of the alarm returning to normal.

Alarm event messages should be sent when a configured alarm activates, returns to normal, or is
acknowledged.

## State

- **Schema Definition:** [state_alarmset.json](../../schema/state_alarmset.json)
 ([_🧬View interactive_](../../gencode/docs/state.html#alarmset))
- [Working `state` Example](../../tests/schemas/state/example.json)

The [state](state.md) message from a device contains an `alarmset` block with the following
structure:

* `alarmset`: Top level block designator.
  * `alarms`: Collection of alarm names.
    * _{`alarm_name`}_: Alarm name.
      * (`status`): Optional [status](status.md) information about this alarm.

## Config

- [🧬Schema](../../gencode/docs/config.html#alarmset)

The [config](config.md) message for a device contains an `alarmset`
block with the following structure:

* `alarmset`: Top level block designator.
  * `alarms`: Collection of alarm names, defining the representative alarm set for this device.
    * _{`alarm_name`}_: Alarm name.
      * `ref`: Reference name of the alarm's internal counterpart.

The alarms defined in the `config.alarmset.alarms` dictionary is the authoritative source
indicating the representative alarms for the device (in both `event` and `state` messages). If
the device has additional alarms that are _not_ stipulated in the config they should be silently
dropped. If the device does not know about a stipulated alarm then it should report it as a
alarm with an _error_ level `status` entry in its `state` message, and exclude it from the event message.
If a `config` block is not present, or does not contain an `alarmset.alarms` object,
then the device should determine on its own which alarms to report.
