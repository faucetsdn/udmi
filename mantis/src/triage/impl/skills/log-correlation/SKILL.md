---
name: log-correlation
description: Strategies for distributed tracing, temporal analysis, and identifying asynchronous race conditions.
---

# Distributed Log Correlation & Trace Analysis

UDMI is an asynchronous, distributed system. A failure at the Sequencer (e.g., a
sync timeout) is often merely the final symptom of a failure that occurred
seconds earlier in a different component. Use these correlation strategies to
stitch the timeline together.

---

## 1. Tracing by Transaction ID (The Primary Key)

The most deterministic way to track a request across network boundaries is the *
*Session Base Transaction ID**.

* **The Strategy:** 1. Identify the transaction ID in `sequence.log` (e.g.,
  `configTransaction RC:18d9b7`).
    2. Follow that ID through the middleware. Search `udmis.log` and
       `pubber.log` for the base ID (e.g., `RC:18d9b7`) to filter out background
       noise and background telemetry.
* **The Goal:** Verify the unbroken chain. Did the Sequencer send it? Did UDMIS
  process and route it? Did Pubber receive it and publish an echo? Did UDMIS
  route the echo back?

---

## 2. Temporal Tracing (The Wider Time Window)

If transaction IDs are missing or if you suspect a broader state corruption
issue, you must use time-based correlation.

* **Avoid Tight Padding:** Do not restrict your analysis exclusively to the
  exact seconds a test was running. If a test times out, the root cause might be
  a misconfiguration or a dropped connection that occurred 30-60 seconds prior
  during the test setup phase.
* **Correlate Cross-Component Timestamps:** Align events by their ISO
  timestamps. If Pubber logs a state change at `12:15:41.000Z`, look at what
  UDMIS was doing at exactly that millisecond.
* **Spot the Silent Failures:** If an event happens at $T_1$ in Pubber, and a
  timeout occurs at $T_2$ in the Sequencer, investigate the silence between
  those timestamps in the UDMIS logs.

---

## 3. Distributed System Failure Heuristics

When investigating unexplained timeouts or state mismatches, do not look for
simple logical typos. Apply these heuristics to hunt for distributed
architecture bugs:

### A. The "Out-of-Order" Race Condition

* **The Concept:** Pub/Sub and MQTT do not guarantee strict FIFO (First-In,
  First-Out) delivery in distributed cloud architectures.
* **What to look for:** Look at the internal timestamps inside the JSON
  payloads, not just the log line timestamps. Did the Sequencer process a packet
  with an older generated timestamp *after* it processed a newer one? This often
  causes state machines to accidentally regress backward.

### B. Shared State and Cache Mutations

* **The Concept:** Middleware like UDMIS is highly multithreaded and processes
  messages from thousands of devices simultaneously.
* **What to look for:** Look for concurrent background threads or telemetry
  streams interacting with the device under test. Did a heartbeat message
  overwrite a routing cache or connection registry right in the middle of the
  Sequencer's active test?

### C. The Silent Transport Drop

* **The Concept:** Sometimes the code is perfect, but the infrastructure fails.
  Queues get saturated, maximum inflight message limits are hit, or QoS (Quality
  of Service) levels cause packets to be dropped without throwing a Java
  exception.
* **What to look for:** The chain of custody breaks cleanly. Pubber logs
  `Publishing state`, but UDMIS never logs receiving it. If this happens, direct
  your `grep_codebase` searches toward the MQTT/PubSub client configurations (
  e.g., connection limits, buffer sizes, QoS settings) rather than the test
  logic.

### D. Clock Drift and Timestamp Desync

* **The Concept:** Physical devices, emulators, and cloud servers rarely have
  perfectly synchronized system clocks. If state-machine logic strictly relies
  on comparing absolute timestamps, a device with a skewed clock will cause
  silent validation failures.
* **What to look for:** Compare the timestamp *inside* the device's JSON payload
  with the ingestion timestamp recorded by UDMIS. If they are skewed by several
  seconds (or minutes), investigate the codebase for strict temporal
  assertions (e.g., `if (device_time < cloud_time) reject()`). A valid packet
  might be rejected simply because the device clock is behind.

### E. Throttling, Quotas, and Backpressure

* **The Concept:** Cloud brokers (like IoT Core) and serverless middleware
  enforce strict rate limits (e.g., max messages per second, max bytes per
  minute). A test that rapidly blasts configurations or a device that spams
  telemetry can easily hit these ceilings.
* **What to look for:** Look for `429 Too Many Requests`, `Resource Exhausted`,
  or sudden, unexplained MQTT connection resets immediately following a
  high-throughput burst of messages. If found, direct your codebase search
  toward backoff logic, retry loops, and batching mechanisms.

### F. Ghost Connections and MQTT Flapping

* **The Concept:** A device loses its network connection and reconnects, but the
  broker hasn't realized the original TCP socket is dead yet (a "ghost
  connection"). In MQTT, this often leads to "Client ID already in use" errors,
  or causes the broker to route configuration packets into the void of the dead
  socket.
* **What to look for:** Look in `pubber.log` and `udmis.log` for rapid
  `CONNECT` / `DISCONNECT` cycling, "Clean Session" flags, or "Keep-Alive"
  timeouts. If the Sequencer sends a config but the device never acknowledges
  it, check if the broker was temporarily confused about which socket was
  actually alive.

### G. Idempotency and Retry Storms

* **The Concept:** If a message is successfully processed but the
  acknowledgment (ACK) is lost in transit, the broker will deliver the exact
  same message a second time. If the receiving component's logic is not
  *idempotent* (safe to run multiple times), this second delivery will corrupt
  the state.
* **What to look for:** Look for the exact same Transaction ID (e.g.,
  `RC:xxxxxx`) being processed *twice* in the `udmis.log` or `pubber.log`.
  Investigate the codebase to see if the state machine handles duplicate
  deliveries gracefully or if it crashes/regresses.

---

## 4. Dynamic Test Facets & Suffix Parameters

Sequencer tests can be dynamically parameterized using a suffix separator (`+`), e.g., `test_name+parameter` (such as `scan_periodic_now_enumerate+vendor`).

* **The Concept:** The suffix (e.g., `vendor`) represents the active facet/parameter value for the test run, typically corresponding to a dynamic family name, point name, or configuration option.
* **What to avoid:** Do NOT search the codebase or logs for exact strings containing the parameter name literal as a hardcoded keyword (e.g., do not assume logs must contain `Sending vendor result for` verbatim).
* **How to trace:** Dynamically trace how the suffix is resolved in the code (e.g., via `getFacetValue()` or matching annotations) and map the parameter value to the corresponding dynamic configuration/state fields (e.g., `scanFamily` or specific family/point keys).