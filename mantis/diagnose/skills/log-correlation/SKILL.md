---
name: log-correlation
description: Correlation strategies and deep analysis of complex race conditions.
---

# Distributed Log Correlation & Race Condition Guide

Distributed asynchronous systems like UDMI produce interleaved streams of messages. This guide explains how to correlate these streams using Session Transaction Keys and Timestamps to uncover race conditions.

---

## 1. The Golden Correlation Key: Session Base Transaction ID

The absolute best way to connect logs across boundaries is the **Session Base Transaction ID**. 

*   **The Pattern:** During the initial test startup sequence, the Sequencer establishes a random Base Transaction ID (e.g., `RC:18d9b7`).
*   **The Lifecycle:**
    1.  **Sequencer (Client):** Logs the base ID: `configTransaction RC:18d9b7`. Every subsequent config packet sent in the same session increments the suffix (e.g., `RC:18d9b7.00000001`, `RC:18d9b7.00000002`).
    2.  **Pubber (Device):** Receives the config packet, and when publishing its state acknowledgment, it copies the received transaction ID into its state block: `state.system.last_config.transaction_id = "RC:18d9b7.00000002"`.
    3.  **UDMIS (Server):** Logs the processing of reflection messages, matching the transaction ID: `ReflectProcessor Processing reflection state/validation ... RC:18d9b7.00000002`.

### How to Correlate:
1.  Find the Base Transaction ID from the local `sequence.log` file by searching for `configTransaction`.
2.  Use this Base ID to search `udmis.log` and `pubber.log` to filter out all logs not belonging to this specific test run.

---

## 2. Correlation by Padded Time-Window

If logs do not explicitly contain transaction IDs, or if you need to analyze global logs (like `udmis.log` or `pubber.log`) during the test, correlate using a **Padded Time-Window**:

1.  Extract the starting timestamp ($T_{start}$) from the test case start NOTICE in `sequence.log` (e.g., `12:15:41Z`).
2.  Extract the ending timestamp ($T_{end}$) from the test case end/failure NOTICE in `sequence.log` (e.g., `12:16:49Z`).
3.  Apply a **5-second padding** to both ends:
    *   $T_{padded\_start} = T_{start} - 5\text{s} = 12:15:36\text{Z}$
    *   $T_{padded\_end} = T_{end} + 5\text{s} = 12:16:54\text{Z}$
4.  Slice `udmis.log` and `pubber.log` for lines falling inside this padded window.

---

## 3. Common Asynchronous Race Condition Patterns

Specialized debugging agents must inspect the correlated timeline for these known architectural race conditions:

### 1. Out-of-Order Pub/Sub Regression Race
*   **The Scenario:**
    1.  The Sequencer starts a test and sends a config reset update `RC:xxxxxx.00000004` to reset `last_start` to `1970-01-01T00:01:13Z`.
    2.  The device boots and publishes its state with actual start time `2026-05-12T12:15:41Z`.
    3.  UDMIS processes the device's state, auto-updates the config to reflect this actual start time, and publishes the config update as `PS:xxxxxx` (version 2406).
    4.  Both packets (`RC:xxxxxx.00000004` and `PS:xxxxxx`) travel via Pub/Sub.
*   **The Race:** Since Pub/Sub does not guarantee strict in-order delivery, the Sequencer processes the newer `PS:xxxxxx` packet FIRST (updating its local expected `last_start` to 2026). Then, it processes the older `RC:xxxxxx.00000004` packet SECOND (regressing its local expected `last_start` backward to 1970).
*   **The Failure:** The Sequencer times out because the device's state remains 2026, while the Sequencer's expected state was reverted to 1970.
*   **Log Evidence:** Look in `sequence.log` for this exact ordering:
    *   `Receiving config update as PS:xxxxxx` (expected updated to 2026).
    *   `Receiving config update as RC:xxxxxx` (expected regressed to 1970).
*   **Proposed Fix:** Modify `SequenceBase.java` to check timestamps and reject config packet reflections that carry older timestamps than the most recently processed configuration packet.

### 2. Transport Registry Routing Affinity Hijack
*   **The Scenario:**
    1.  UDMIS maintains a single, global in-memory cache (`activeProviders`) mapping registry names to the transport channel of the most recently active client (e.g., mapping `UDMI-REFLECT/VAV-501` to `pubsub+bpakenham`).
    2.  The Sequencer runs a test on registry `UDMI-REFLECT/VAV-501` using a `pubsub` client connection.
    3.  Concurrently, a background tool (like `MappingService`) sends a heartbeat state update via a `clearblade` channel on the same registry.
*   **The Race:** UDMIS processes the heartbeat and overwrites the registry's cached routing affinity map to `clearblade-iot-core`.
*   **The Failure:** When UDMIS receives a reply for the Sequencer's active transaction, it resolves the provider from the globally hijacked cache, sending the reply to the ClearBlade queue. The Sequencer (listening on PubSub) misses the reply and times out after 60 seconds.
*   **Log Evidence:**
    *   `ReflectProcessor Processing reflection` showing background service active.
    *   `DynamicIotAccessProvider` switching resolved route to `clearblade-iot-core` in the middle of the test.
*   **Proposed Fix:** Modify UDMIS's `DynamicIotAccessProvider.java` to extract and route replies directly using the transaction's `envelope.source` (e.g., `pubsub+bpakenham`) rather than checking the global mutable cache map.
