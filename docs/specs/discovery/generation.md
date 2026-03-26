[**UDMI**](../../../) / [**Docs**](../../) / [**Specs**](../) / [**Discovery**](./) / [Generation](#)

# Discovery Generation

The `generation` field is a core mechanism in UDMI discovery and enumeration processes. It is used to trigger, track, and correlate discovery scans and self-enumeration requests. The `generation` value is an RFC 3339 formatted date-time string.

Depending on the context (e.g., self-enumeration vs. family scanning, sporadic vs. periodic), the `generation` field behaves differently. This document summarizes these different use cases and expectations.

## 1. Passive Scanning

A passive scan monitors the network without actively sending out probes. It operates continuously or based on network activity.

* **Trigger**: A passive scan does not use a `generation` marker to trigger. It is configured simply by defining passive scan parameters (e.g., `passive_holdoff_sec`).
* **State**: The `phase` will report as `passive` or `active`. There is typically no `generation` field reported in the state for a purely passive scan, as there is no specific scan cycle to track.
* **Events**: Generated discovery events do not include a `generation` value.

## 2. Active Scanning (Sporadic / One-Time)

A sporadic scan is an explicit, one-off request for a device to actively scan a network family for other devices.

* **Trigger**: Initiated by setting the `generation` field in the target family configuration (`config.discovery.families.<family>.generation`) to a new timestamp (which must be after the device's last start time and different from the previous scan generation).
* **State**:
    * When the scan is scheduled or waiting to start, the family `state` block reports a `phase` of `pending` and its `generation` will match the `config`'s `generation`.
    * When the scan begins, the `phase` changes to `active`.
    * Once the scan completes, the `phase` changes to `stopped` (or the active indicator is removed). Ideally, the state retains the `generation` field to indicate the timestamp of the last scan performed.
* **Events**: Each discovery event produced as a result of this scan will include the matching `generation` value, securely correlating the discovered devices with the specific scan trigger.
* **Completion**: After the scan concludes, the `generation` entry in the `config` block can either be removed (with no effect on the device) or updated to a new timestamp to trigger another sporadic scan.

## 3. Active Scanning (Periodic / Recurring)

A periodic scan is an active scan that repeats automatically at defined intervals, eliminating the need to update the configuration for every scan.

* **Trigger**: Configured by providing both a `generation` timestamp and a `scan_interval_sec` parameter in the family configuration. The `generation` timestamp serves as the **base time** (or initial start time) for the interval schedule.
* **Execution Interval**: Scans occur at scheduled intervals strictly determined by the base `generation` timestamp plus increments of the `scan_interval_sec` (i.e., `Ts = Tc + N * Ti`). This ensures there is no clock drift over time.
* **State & Events**:
    * Unlike the sporadic scan where the state `generation` directly matches the config `generation`, in a periodic scan, the device **updates the `generation` value for each loop** to uniquely identify the current execution.
    * This loop-specific `generation` field will be greater than or equal to the base `generation` specified in the `config`.
    * This unique `generation` timestamp is reported in the `state` block during the scan and attached to all discovery events produced during that loop.
* **Termination**: The recurring loop terminates when either the `generation` field or the `scan_interval_sec` parameter is removed from the `config`.

## 4. Self Enumeration

Self-enumeration is an explicit request for a single, already-registered device to describe its own capabilities (points, features, etc.), rather than scanning the network for other devices.

* **Trigger**: Initiated by setting the `generation` parameter in the root `discovery` block (`config.discovery.generation`), as opposed to a specific family block.
* **State**: The `state.discovery` block reflects the `generation` currently being processed.
* **Events**: Generated discovery events contain the corresponding `generation` value. Because these events originate from the device itself rather than a proxy scan, they do not include a `family` block; the identity is determined from the message envelope.

## Summary Matrix

| Mode | Location in `config` | Trigger / Role | `state` Behavior | `events` Behavior |
| --- | --- | --- | --- | --- |
| **Passive Scan** | N/A | None required. | No `generation`. | No `generation`. |
| **Active Sporadic** | `families.<family>.generation` | Explicit timestamp triggers a one-off scan. | Matches `config.generation`. Retained after stop. | Matches `config.generation`. |
| **Active Periodic** | `families.<family>.generation` + `scan_interval_sec` | Base timestamp for calculating the schedule. | Updates every loop to a new, unique timestamp `≥ config.generation`. | Matches the current loop's updated `generation`. |
| **Self Enumeration** | `discovery.generation` | Explicit timestamp triggers self-reporting. | Matches `config.generation`. | Matches `config.generation`. |
