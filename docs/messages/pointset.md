[**UDMI**](../../) / [**Docs**](../) / [**Messages**](./) / [System](#)

# Pointset

# Pointset Specification

A `pointset` represents a set of points reporting telemetry data. This is the most common data, and should be stored in an appropriate time-series database.

Specific `point_names` within a `pointset` should be specified in _snake_case_ and adhere to the
data ontology for the device (stipulated and verified outside of UDMI, e.g. [Digital Buildings Ontology](https://github.com/google/digitalbuildings/tree/master/ontology)).


Pointset is represented in four locations:
- [Metadata](#metadata)
- [Event](#event)
- [State](#state)
- [Config](#config)

## Metadata

- **Schema Definition:** [model_pointset.json](../../schema/model_pointset.json)
 ([_🧬View_](../../gencode/docs/metadata.html#pointset))
- [Working `metadata` Example](../../tests/schemas/metadata/example.json)

The `metadata.pointset` subblock represents the abstract system expectation for what the device
_should_ be doing, and how it _should_ be configured and operated. This block specifies the
expected points that a device holds, along with, if the field is numeric, the expected units of those points.
The general structure of a `pointset` block exists inside of a complete metadata message

* `pointset`: Top level block designator.
  * `points`: Collection of point names.
    * _{`point_name`}_: Point name.
      * `units`: Expected units designation for this point.

## Event

- **Schema Definition:** [events_pointset.json](../../schema/events_pointset.json)
 ([_🧬View_](../../gencode/docs/events_pointset.html#points))
- [Working `events_pointset` Example](../../tests/schemas/events_pointset/example.json)

A basic `pointset` event message contains
the point data sent from a device. The message contains just the top-level `points` designator,
while the `pointset` typing is applied as part of the [message envelope](envelope.md).

* `points`: Collection of point names.
  * _{`point_name`}_: Point name.
    * `present_value`: The specific point data reading. If the value represents a number, then this must be serialized as a JSON number, not a quoted string.
* `partial_update`: Optional indicator if this is an incremental update (not all points included).

Event telemetry messages should be sent "as needed" or according to specific requirements as
stipulated in the `config` block. The basic `pointset` event message for a device should
contain the values for all representative points from that device, as determined by the associated
`config` block. If no points are specified in the `config` block, the device programming determines
the representative points.

### Incremental Updates and CoV

Incremental updates (e.g. for COV) can send only the specific updated points as an optimization,
while setting the top-level
[🧬`partial_update`](../../gencode/docs/events_pointset.html#partial_update) field to `true` These
messages may be indiscriminately dropped by the backend systems, so a periodic full-update must
still be sent (as per `sample_rate_sec` below). Sending an update where all expected points are not
included, without this flag, is considered a validation error.

## State

- **Schema Definition:** [state_pointset.json](../../schema/state_pointset.json)
 ([_🧬View interactive_](../../gencode/docs/state.html#pointset))
- [Working `state` Example](../../tests/schemas/state/example.json)

The [state](state.md) message from a device contains a `pointset` block with the following
structure:

* `pointset`: Top level block designator.
  * `points`: Collection of point names.
    * _{`point_name`}_: Point name.
      * (`status`): Optional [status](status.md) information about this point.
      * (`value_state`): Optional enumeration indicating the 
        [state of the points value.](../specs/sequences/writeback.md#value_state)

In all cases, the points `status` field can be used to supply more information (e.g., the
reason for an _invalid_ or _failure_ `value_state`).

## Config

- [🧬Schema](../../gencode/docs/config.html#pointset)
- [Working `config` Example](../../tests/schemas/config/writeback.json)

The [config](config.md) message for a device contains a `pointset`
block with the following structure:e

* `pointset`: Top level block designator.
  * `sample_rate_sec`: Maximum time between samples for the device to send out a _complete_
  update. It can send out updates more frequently than this.
  * `sample_limit_sec`: Minimum time between sample updates for the device (including complete
  and COV updates). Updates more frequent than this should be coalesced into one update.
  * `points`: Collection of point names, defining the representative point set for this device.
    * _{`point_name`}_: Point name.
      * `units`: Set as-operating units for this point.
      * (`set_value`): Optional setting to control the specified device point. If the set_value represents a number, then this must be serialized as a JSON number, not a quoted string. See [writeback documentation](../specs/sequences/writeback.md).
      * (`cov_threshold`): Optional threshold for triggering a COV event update.

The points defined in the `config.pointset.points` dictionary is the authoritative source
indicating the representative points for the device (in both `event` and `state` messages). If
the device has additional points that are _not_ stipulated in the config they should be silently
dropped. If the device does not know about a stipulated point then it should report it as a
point with an _error_ level `status` entry in its `state` message, and exclude it from the event message.
If a `config` block is not present, or does not contain a `pointset.points` object,
then the device should determine on its own which points to report.

If `sample_rate_sec` is not defined (or zero), then the system is expected to send an update at least every
300 seconds (5 minutes as a default value). A negative value would mean "don't send updates."
