# 🧪 Tool Specification: Sequencer Tool

This document defines the functional capabilities, data inputs, process controls, and output telemetry requirements for the **Sequencer** compliance test tool.

---

## 1. Domain Purpose

The **Sequencer** tool provides an interactive capability to execute UDMI compliance test suites against target site model devices, inspect per-device test pass/fail result matrices, and monitor live streaming console logs.

---

## 2. Functional Capabilities & Controls

### 2.1 Test Execution Inputs & Parameters
- **Target Device Selection**: Selector listing devices discovered in active site model, plus an "All Devices" scope filter.
- **Target Environment Config (`project_spec`)**: Structured parameters (`provider`, `project`, `namespace`, `user`) matching UDMI rules `[//provider/]project[/namespace][+user]`:
  - *Provider Selector*: Choice of `gbos`, `gref`, `mqtt`, `pubsub`, `clearblade`.
  - *Project Identifier*: GCP Project ID or broker hostname (e.g. `bos-platform-dev`, `localhost`).
  - *Namespace Segment (Optional)*: Logical registry/topic prefix (`/namespace` -> `namespace~...`).
  - *User Segment (Optional)*: User isolation suffix (`+user`). Disabled when `gbos` or `mqtt` provider is chosen.
  - *Syntax Preview*: Live generated preview string e.g. `//gref/bos-platform-dev/faucetsdn+heykhyati`.
- **Serial Number Override**: Optional text input to override target device serial number.
- **Minimum Test Stage Filter**: Selection of minimum test stage threshold (`ALPHA`, `BETA`, `PREVIEW`, `STABLE`).
- **Execution Process Action**: Action control to launch (`Run Sequencer`) or terminate (`Stop Sequencer`) active execution runs.

### 2.2 Device & Compliance Status Matrix
- Displays test suite results grouped by device ID.
- Displays per-test status indicators (`PASSED`, `FAILED`, `SKIPPED`, `RUNNING`, `NOT_RUN`).
- Displays execution timestamps and test stage classifications (`ALPHA`, `BETA`, `PREVIEW`).
- Failure Action Trigger: Direct action on a failed test entry to launch AI Failure Triage for that test failure.

### 2.3 Terminal Console Log Streamer
- Real-time log streaming output subscribing to `/api/stream`.
- Log severity formatting (`INFO`, `WARN`, `ERROR`, `PASS`, `FAIL`).
- Console Controls: Clear console output, search/filter log text, and auto-scroll locking during historical log inspection.
