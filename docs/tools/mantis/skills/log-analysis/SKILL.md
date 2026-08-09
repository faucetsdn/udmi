---
name: log-analysis
description: Factual reference for diagnostic log streams, distributed tracing, and strategies for handling missing telemetry.
---

# UDMI Diagnostic Log Analysis & Tracing

UDMI is an asynchronous, distributed system. A failure at the Sequencer (e.g., a
sync timeout) is often merely the final symptom of a failure that occurred
seconds earlier in a different component. 

---

## 1. Log File Catalogs & Scope

When investigating an issue, pick the right log file for the scope of your hypothesis.

*   **`sequence.log`:** Local to the Sequencer client. This is your starting point. It contains test boundaries, assertion failures, timeouts, and the initial dispatch of the Session Base Transaction ID (`RC:xxxxxx`). Do not assume a failure logged here originated here.
*   **`sequence.md`:** Local to the Sequencer. Highly valuable for investigating **Schema Violations**. Renders JSON payloads side-by-side.
*   **`udmis.log`:** Global Cloud Pod Log. The truth-teller of the network. Records exact routing translations, dynamic provider resolutions, and validation exceptions. MUST filter using Transaction IDs or timestamps.
*   **`pubber.log`:** Global Device Emulator Log. Logs the internal state machine of the simulated building hardware. If tests are run against real physical hardware, this file will be empty or absent.

---

## 2. Tracing Strategies

### A. Tracing by Transaction ID (The Primary Key)
1. Identify the transaction ID in `sequence.log` (e.g., `configTransaction RC:18d9b7`).
2. Follow that ID through the middleware. Search `udmis.log` and `pubber.log` for the base ID (e.g., `RC:18d9b7`) to filter out background noise.
3. Verify the unbroken chain. Did the Sequencer send it? Did UDMIS process and route it? Did Pubber receive it and publish an echo? Did UDMIS route the echo back?

### B. Temporal Tracing (The Wider Time Window)
*   **Avoid Tight Padding:** If a test times out, the root cause might be a misconfiguration or dropped connection that occurred 30-60 seconds prior during the test setup phase.
*   **Correlate Cross-Component Timestamps:** Align events by their ISO timestamps. Look at what UDMIS was doing at the exact millisecond Pubber logged a state change.

### C. Distributed System Failure Heuristics
*   **The "Out-of-Order" Race Condition:** Pub/Sub and MQTT do not guarantee strict FIFO delivery. Look at internal timestamps inside JSON payloads.
*   **Shared State and Cache Mutations:** Look for concurrent background threads interacting with the device under test in `udmis.log`.
*   **The Silent Transport Drop:** The chain of custody breaks cleanly (e.g., Pubber publishes, but UDMIS never receives). Direct searches toward MQTT/PubSub client configurations.
*   **Clock Drift and Timestamp Desync:** Compare the timestamp *inside* the device's JSON payload with the ingestion timestamp recorded by UDMIS.
*   **Throttling, Quotas, and Backpressure:** Cloud brokers enforce strict rate limits. Look for QoS drops.

---

## 3. Best-Effort Triage (Handling Incomplete Telemetry)

A senior debugging agent does not give up when a log stream is missing. If global logs (`udmis.log`, `pubber.log`) are absent, treat the missing component as a "black box" and rely on what the Sequencer sent versus what it ultimately received.

If the telemetry is insufficient to mathematically guarantee the root cause:
1.  **Isolate the Last Known Good State.** Identify the exact transaction ID or timestamp where the system was behaving correctly just before the failure.
2.  **State Your Assumptions.** Explicitly document assumptions (e.g., "Assuming the device received the configuration payload...").
3.  **Propose a Primary Hypothesis** based strictly on the codebase logic and the logs you *do* have.
4.  **Identify Missing Context:** Explicitly state what data was missing that prevented a definitive conclusion.
