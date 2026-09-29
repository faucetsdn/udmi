---
name: state-machine-investigation
description: Methodological playbook for investigating the UDMI device-sequencer state synchronization cycle, cutoffs, and config timestamp handshakes.
---

# UDMI State Machine & Synchronization Investigation Playbook

## 1. Principle: Source Code Implementation is the Contract
The definitive state machine rules are implemented in the Java sequencer base classes:
* `validator/src/main/java/com/google/daq/mqtt/sequencer/SequenceBase.java`
* `validator/src/main/java/com/google/daq/mqtt/sequencer/SequenceRunner.java`

When diagnosing state synchronization issues, read `SequenceBase.java` to confirm the exact waiting logic, cutoff comparison, and assertion predicates.

## 2. The Config-State Synchronization Loop
The UDMI state contract follows a strict bidirectional handshake:
1. **Config Dispatch**: The sequencer or cloud backend dispatches a config update with an RFC 3339 `timestamp` (e.g. `2024-03-15T12:00:00Z`). In cloud reflector operations, command messages may also carry an `RC:<hash>.<seq>` transaction ID prefix.
2. **Stale State Cutoff**: At sequencer JVM startup, `SequenceBase` initializes a static `stateCutoffThreshold` (`SequenceBase.java:255`), logged as `Stale state cutoff threshold is <ISO-timestamp>`.
3. **State Ingestion & Staleness Check**: Incoming state updates are checked in `SequenceBase.java` (~lines 2131–2148):
   - Before the initial device state is accepted (`deviceState == null`), any state update whose `timestamp` is before `stateCutoffThreshold` is discarded as stale (`ignoring stale state update`).
   - If the device's `metadata.json` specifies `"testing": {"nostate": true}`, the sequencer skips initial state waiting entirely (`SequenceBase.java:916-921`).
4. **Device State Echo**: The device must apply the config and publish a state update satisfying:
   - `state.timestamp >= stateCutoffThreshold`
   - `state.system.last_config`: Must equal the RFC 3339 `timestamp` of the last successfully parsed config update (`schema/state_system.json`).
5. **Stage Confirmation**: Once matched, the sequencer transitions to the next stage or marks the test `PASS`.

## 3. Investigation Procedure for State & Timeout Failures
1. **Detect Cutoff Rejections**: Run `detect_log_anomalies(run_dir="<run_dir>")`. If `STALE_STATE_CUTOFF` is present, the device clock may lag behind the orchestrator, or the device driver may be polling/caching on a long interval exceeding the cutoff window.
2. **Extract Timeline**: Run `get_test_timeline(test_id="...", device_id="...", run_dir="<run_dir>")` to inspect the chronological order of config dispatches, received state messages, and cutoff threshold checks.
3. **Inspect Message Trace**: Run `inspect_message_trace(run_dir="<run_dir>", message_type="state")` to inspect the raw JSON state payload sent by the device. Check:
   - Is `timestamp` present and RFC 3339 compliant?
   - Does `system.last_config` match the timestamp of the dispatched config message?
4. **Inspect Sequencer Wait Logic**: Use `read_udmi_file("validator/src/main/java/com/google/daq/mqtt/sequencer/SequenceBase.java", start_line=..., end_line=...)` to inspect the state evaluation and cutoff conditions.
