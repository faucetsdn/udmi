---
name: log-sources
description: Factual reference for the directory structure and purpose of UDMI diagnostic log streams.
---

# UDMI Diagnostic Log Sources Reference

This guide catalogs the layout of a standard UDMI diagnostic run. Use this map
to locate the correct file paths for your tools, and understand the
architectural scope of each log stream.

---

## 1. Support Package Directory Structure

A standard test execution generates the following artifact hierarchy. Note the
separation between global runs and isolated device tests.

```text
run_X/
├── sequencer.out            # Summary of sequencer execution outcomes (JUnit format)
├── test_itemized.out        # Summary of itemized schema tests (if run)
├── pubber.log               # Global stdout/stderr from the device emulator
├── udmis.log                # Global stdout/stderr from the cloud middleware pod
└── out/
    └── devices/
        └── <device_id>/
            └── tests/
                └── <test_name>/
                    ├── sequence.log  # Console log isolated to this specific test case
                    └── sequence.md   # Markdown trace containing raw JSON payloads

```

---

## 2. Log File Catalogs & Scope

When investigating an issue, pick the right log file for the scope of your
hypothesis.

### 1. The Local Test Sequence Log (`sequence.log`)

* **Path:** `out/devices/<device_id>/tests/<test_name>/sequence.log`
* **Scope:** Local to the Sequencer client.
* **Diagnostic Value:** This is your starting point. It contains test
  boundaries (Starting/Ending), assertion failures, wait-loop timeouts, and the
  initial dispatch of the Session Base Transaction ID (`RC:xxxxxx`).
* **Warning:** Do not assume a failure logged here originated here. The
  Sequencer often just reports the downstream failure of another component.

### 2. The Local Test Markdown Trace (`sequence.md`)

* **Path:** `out/devices/<device_id>/tests/<test_name>/sequence.md`
* **Scope:** Local to the Sequencer client.
* **Diagnostic Value:** This file is highly valuable for investigating **Schema
  Violations**. It renders the actual JSON payloads (Configs sent, States
  received) side-by-side, allowing you to easily verify if a property is
  missing, null, or incorrectly typed before diving into the Java serializers.

### 3. The Global Cloud Pod Log (`udmis.log`)

* **Path:** `udmis.log`
* **Scope:** Global. Contains interleaved traffic from all devices and tests
  running concurrently.
* **Diagnostic Value:** The truth-teller of the network. It records exact
  routing translations, dynamic provider resolutions (e.g., `google-iot-core` vs
  `clearblade`), and validation exceptions thrown by the middleware. You MUST
  filter this file using Transaction IDs or timestamps to isolate the test
  traffic.

### 4. The Global Device Emulator Log (`pubber.log`)

* **Path:** `pubber.log`
* **Scope:** Global emulator runtime.
* **Diagnostic Value:** Logs the internal state machine of the simulated
  building hardware. Useful for verifying if the device ever established an MQTT
  connection, if it rejected a configuration update, or if its internal timers
  crashed.
* **Black-Box Note:** If tests are run against real physical hardware, this file
  will be empty or absent.

---

## 3. Summary Indexes

Do not read these files for deep debugging; they are just summary scorecards.

* **`sequencer.out` / `test_itemized.out**`
* **Format:** `RESULT <pass|fail> <category> <test_name> <details>`
* **Example:**
  `RESULT fail system.last_update valid_serial_no last_start not synced`