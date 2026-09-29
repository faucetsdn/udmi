---
name: log-anomaly-hunting
description: Methodological playbook for chronologically correlating multi-service logs, detecting anomalies, and extracting boundary payloads.
---

# Distributed Log Observability & Anomaly Hunting Playbook

## 1. Principle: Cross-Service Correlation Across Time
In distributed testing, a high-level symptom (such as an orchestrator timeout) is frequently caused by an earlier failure in the device, gateway, transport, or backend message processor.

Never treat a `TIMEOUT` or generic `FAIL` as the root cause without investigating the timeline of all constituent service logs:
* `sequence.log`: Orchestrator expectations, timeouts, cutoffs, and assertions.
* `pubber.log` / `device_system.log`: Device application behavior and received packets.
* `udmis.log`: Backend message processing, reflection, and state sharding.
* `validator.log`: Schema compliance checks and error collector reports.

## 2. Investigation Procedure
1. **Automated Anomaly Scan**: Run `detect_log_anomalies(run_dir="<run_dir>")` to instantly surface:
   - Jackson deserialization errors (`UnrecognizedPropertyException`, `JsonParseException`).
   - Stale state cutoff rejections (`ignoring stale state update`).
   - Transport and authentication refusals (`ConnectionRefusedError`, `Bad username or password`).
   - Broker congestion and in-flight token starvation (`inFlight tokens`, `queue full`).
2. **Chronological Alignment**: Run `get_test_timeline(test_id="...", device_id="...", run_dir="<run_dir>")` to construct a unified chronological sequence of events across all logs. Identify the exact step where behavior first diverged from nominal.
3. **Boundary Payload Inspection**: Run `inspect_message_trace(run_dir="<run_dir>")` to inspect the exact recorded JSON payloads exchanged over MQTT (`events_pointset.json`, `state.json`, `config.json`). Check if fields match the schema.
4. **Targeted Log Inspection**: Use `get_test_logs(test_id="...", run_dir="<run_dir>")` to inspect relevant log segments around the time of divergence.
