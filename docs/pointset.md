# Pointset Specification

A `pointset` represents a set of points reporting telemetry data. This is the most common data
and store them in an appropriate time-series database.

Specific `point_names` within a `pointset` should be specified in _snake_case_ and adhere to the
data ontology for the device (stipulated and verified elsewhere).

## Metadata

The `metadata.pointset` subblock represents the abstract system expectation for what the device
_should_ be doing, and how it _should_ be configured and operated. This block specifies the
expected points that a device holds, along with (optionally) the expected units of those points.
The general structure of a `pointset` block exists inside of a complete
[metadata](../schema/metadata.tests/example.json) message.

* `pointset`: Top level block designator.
  * `points`: Collection of point names.
    * _{`point_name`}_: Point name.
      * `units`: Expected units designation for this point.

## Telemetry

A basic `pointset` [telemetry](../schema/pointset.tests/example.json) message contains
the point data sent from a device. The message contains just the top-level `points` designator,
while the `pointset` typing is applied as part of the [message envelope](envelope.md).

* `points`: Collection of point names.
  * _{`point_name`}_: Point name.
    * `present_value`: The specific point data reading.

Telemetry update messages should be sent "as needed" or according to specific requirements as
stipulated in the `config` block. The basic `pointset` telemetry message for a device should
contain the values for all representative points from that device, as determined by either
device programming or the associated `config` block. Incremental updates (e.g. for COV) can
send only the specific updated points as an optimization.

## State

The [state](../schema/state.tests/example.json) message from a device contains a `pointset`
block with the following structure:

* `pointset`: Top level block designator.
  * `points`: Collection of point names.
    * _{`point_name`}_: Point name.
      * (`status`): Optional [status](status.md) information about this point.
      * (`source`): Optional enumeration indicating the source of the points value.

Valid `source` settings include:
* _<empty>_: No `fix_value` _config_ has been specified, the source is device-native.
* _applied_: The `fix_value` _config_ has been successfully applied.
* _overridden_: The _config_ setting is being overridden by another source.
* _invalid_: A problem has been identified with the _config_ setting.
* _failure_: The _config_ is fine, but a problem has been encountered appling the setting.

In all cases, the points `status` field can be used to supply more information (e.g., the
reason for an _invalid_ or _failure_ `mode`).

## Config

The [config](../schema/config.tests/example.json) message for a device contains a `pointset`
block with the following structure:

* `pointset`: Top level block designator.
  * `sample_rate_sec`: Maximum time between samples for the device to send out a _complete_
  update. It can send out updates more frequently than this.
  * `sample_limit_sec`: Minimum time between sample updates for the device (including complete
  and COV updates). Updates more frequent than this should be coalesced into one update.
  * `points`: Collection of point names, defining the representative point set for this device.
    * _{`point_name`}_: Point name.
      * `units`: Set as-operating units for this point.
      * (`fix_value`): Optional setting to control the specificed device point.
      * (`cov_threshold`):  Optional threshold for triggering a COV telemetry update.

The points defined in the `config.pointset.points` dictionary is the authoritative source
indicating the representative points for the device (in both `telemetry` and `state` messages). If
the device has additional points that are _not_ stipulated in the config they should be silently
dropped, and if the device does not know about a stipulated point then it should report it as a
point with an _error_ level `status` entry in its `state` block. If a `config` block is not present,
or does not contain a `pointset.points` object, then the device should determine on its own
which points to report.
