[**UDMI**](../../../) / [**Docs**](../../) / [**Specs**](../) / [**Sequences**](./) / [Discovery](#)

# Discovery Sequences

The basic discovery device message sequence follows a standard config/state call/response mechanism, with
slightly different parameters for each different mode of operation. During the process, there's two major
devices involved: the _enumerated_ node (the thing with the `refs` that are being described), and the
_discovery_ node (the thing that is doing the scan, which does not exist in the _self enumeration_ case).

There's two basic kinds of discovery _scan_ capabilities:
* [Passive Scan](#passive-scan): Passively monitor the protocol channel to detect target nodes.
* [Active Scan](#active-scan): Actively probe the protocol channel to find target nodes.
  * [Sporadic Scan](#sporadic-scan): A single scan request to discover potentially unknown on-network devices.
  * [Periodic Scan](#periodic-scan): Periodically scan for unknown devices on the network.

The _passive_ and _active_ scan configurations are not mutually exclusive, and can be setup to, e.g.,
continually perform a passive scan of a network while periodically actively probing for devices.

Likewise, a few different ways discovery _enumeration_ can happen:

* [Self Enumeration](#self-enumeration): An enumeration request for a single directly connected device.
* [Scan Enumeration](#scan-enumeration): Enumerate device capabilities as part of a discovery scan.

## Scan State Phases

There are four phases that a discovery system can be in, as reported by the appropriate _state_ block.

* `stopped`: There is no scan activity, either passive, active, or scheduled.
* `passive`: The system is passively monitoring for devices on the network.
* `pending`: The system has a future active scan scheduled.
* `active`: There is currently an active scan in progress.

If both an _active_ and _passive_ scan are configured, then the reported phase should be `passive`
until the `active` phase applies (so there would be no `pending` phase indicated).

## Passive Scan

A _passive_ scan is the mode for a system that can passively monitor traffic and deduce scan results, so
there is no strict need for a _sporadic_/_periodic_ scan. This might be a system that, e.g., monitors network
ARP requests or transient BACnet traffic.

* [_start config_](../../../tests/schemas/config/continuous.json): There is no `generation` marker, since
  scanning is always happening. Specification of a `passive_holdoff_sec` value enables the passive scan.
* [_start state_](../../../tests/schemas/state/continuous.json): Indicates that scanning is `active`, but no `generation` value.
* [_discovery events_](../../../tests/schemas/events_discovery/continuous.json): Events as per normal, except no `generation` value.

The `passive_holdoff_sec` field indicates the duration within which a scan result for a given device should _not_
be repeated. E.g., if a device is passively detected every _30 sec_, but the scan interval is _60 sec_, then
the result would only be reported for (approximately) every other detection.

## Active Scans

(Note: the information below is provisional and known not accurate... pending a documentation update!)

### Sporadic Scan

a _sporadic scan_ is used to trigger an on-prem agent (often an IoT Gateway) to scan the local network for devices.
Depending on the system, this might encompass a number of different network protocols (e.g. BACnet, IPv4, etc...).

* [_start config_](../../../tests/schemas/config/discovery.json): Starts a discovery scan, triggered
  by the `generation` timestamp (defined, not-the-same as the previous scan generation, and after the device's last start time).
* [_start state_](../../../tests/schemas/state/discovery.json): Indicates the device is actively
  scanning, with `generation` should match that of _config_, and the `phase` is indicated as `pending`.
* [_discovery events_](../../../tests/schemas/events_discovery/discovery.json): Streaming results
  for scanned devices (keyed by matching `generation` field): one _events_ for each unique device scanned.
* [_stop state_](../../../tests/schemas/state/scan_stop.json): Once complete, the _active_ field is `false`
  (or removed). Ideally the `generation` field would remain to indicate the last scan performed.

At this point, the _config_ `generation` entry can be removed with no effect, or updated to initiate a new scan.

### Periodic Scan

A _periodic scan_ is like a _sporadic scan_ except that the scan automatically occurs due to a predefined
interval (rather than individual trigger _config_s). This allows for repeated scans without any _config_ changes.

* [_start config_](../../../tests/schemas/config/periodic.json): Sets up a periodic scan, as defined by the
  `scan_interval_sec` parameter.
* Loop over the { _start_, _discovery_, _stop_ } sequence as per a _sporadic scan_:
  * The `generation` value each loop will be updated to uniquely identify the current loop.
  * Unlike the _sporadic_ case, the `generation` field will be greater than or equal to the _config_ specification.
  * Loop terminates when either the `generation` or `scan_interval_sec` parameter is removed from _config_.

Note that the _scanning_ should occur at intervals directly determined by the _config_ `generation` timestamp plus
integral increments of the scan _interval_, i.e. _Ts = Tc + N*Ti_, so that there is no clock drift.  E.g., it
should be possible to setup a schema to scan every day exactly at midnight.

## Enumeration Mechanisms

### Self Enumeration

_Self enumeration_ is used for a device that is already registered in the cloud systems (no scan required),
and can be explicitly directed to enumerate itself. This also applies to all direct-connect (not proxy) devices
(which likely can't be scanned anyway)

* [_start config_](../../../tests/schemas/config/enumeration.json): `generation` parameter in the `system`
  block starts the self enumeration process (rather than the `discovery` block).
* [_start state_](../../../tests/schemas/state/enumeration.json): The `system` block indicates the `generation`
  of enumeration that is currently being processed.
* [_discovery events_](../../../tests/schemas/events_discovery/enumeration.json): The results do not have a `family` block,
  rather, the device id is determined from the envelope's `deviceId` field.

With self enumeration there is no specific _stop state_, as the system deterministically sends a single device's
_discovery events_ corresponding to the _config_ trigger.

### Scan Enumeration

_Scan enumeration_ comes bundled with a discovery _scan_ of some kind, triggered by the `enumeration` field
in the [_start config_](../../../tests/schemas/config/periodic.json) indicates that the system should also
then automatically enumerates each device encountered.

* [_start config_](../../../tests/schemas/config/implicit.json): Initiates the scan, along with an added
  `enumerate` field indicating that the system should enumerate each device it encounters.
* [_start state_](../../../tests/schemas/state/discovery.json): Same as base scan case.
* [_discovery events_](../../../tests/schemas/events_discovery/implicit.json): Same as _scan_ result, except
  includes enumeration fields (typically discovered `points`).
* [_stop state_](../../../tests/schemas/state/scan_stop.json): Same as base scan case.

## Error Handling

There's different ways to report errors, depending on the scope of the error.

* [_scan error_](../../../tests/schemas/state/scan_error.json): Exemplifies how a device should report an error
potentially affecting all _devices_ or points during a scan.
* [_self error_](../../../tests/schemas/state/enumeration.json): Details status while processing _self_ enumeration
  that potentially affects all _points_.
* [_point error_](../../../tests/schemas/events_discovery/point_error.json): Details how an _individual_ point error
  should be reported during (_self_ or _scan_) enumeration.
* [_scan enumeration error_](../../../tests/schemas/events_discovery/scan_error.json): Details how a _scan_ enumeration
  error that affects all points should be reported (i.e. while trying to enumerate the scanned device).
