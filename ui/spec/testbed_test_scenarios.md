# UDMI Testbed Comprehensive Test Scenarios Matrix

This document details the test scenarios required to validate that the UDMI Testbed interactive workspace correctly handles topology creation, logical routing, device-only palette drag-and-drop, controlled pipeline infrastructure, completeness validation, health checking, dynamic port binding, and conflict prevention.

---

## Matrix Overview

| Category | Test Scenario ID | Scenario Name | Target Environment / Topologies |
| :--- | :--- | :--- | :--- |
| **Pipeline Setup Modes** | `TS-1.1` | Standard Local Pipeline Mode | Pickable Devices $\rightarrow$ Managed Local Mosquitto $\rightarrow$ Managed Local UDMIS |
| | `TS-1.2` | Standard Cloud Pipeline Mode | Pickable Devices $\rightarrow$ Managed ClearBlade Broker $\rightarrow$ Managed Cloud UDMIS |
| | `TS-1.3` | Physical Hardware Pipeline | Actual Device $\rightarrow$ Managed Local Mosquitto $\rightarrow$ Managed Local UDMIS |
| | `TS-1.4` | Device Palette Drag-and-Drop | Adding Pubber & Actual Devices onto active pipeline canvas |
| **Controlled Pipeline Rules** | `TS-2.1` | Setup Mode Toggle | Switching Local $\leftrightarrow$ Cloud Setup preserves canvas devices |
| | `TS-2.2` | Disallowed Hybrid Direct Placement | Palette restricts drag-and-drop to pickable device nodes only |
| **Completeness & Validation** | `TS-3.1` | Complete Topology Validation | Green 🟢 `COMPLETE SETUP (LOCAL/CLOUD PIPELINE)` |
| | `TS-3.2` | Incomplete Device Palette Canvas | Warning ⚠️ `INCOMPLETE: Drag a device onto the canvas` |
| | `TS-3.3` | Disabled Device Rejection | Dragging disabled Spotter or Ancillary nodes |
| **Runtime & Execution** | `TS-4.1` | Non-Sudo Dynamic Port Selection | `UDMI_NO_SUDO=true` dynamic port allocation |
| | `TS-4.2` | Device Registration Prerequisite | `bin/registrar` validation prior to Sequencer |
| | `TS-4.3` | Message-Based Health Probe | Telemetry heartbeat verification via `bin/pull_mqtt` |
| | `TS-4.4` | Component Lifecycle State Sync | Transitioning node status with CSS spinner loader |
| **UI & Canvas** | `TS-5.1` | Smooth Dragging & Offset | Node selection drag without top-left position jump |
| | `TS-5.2` | Inspector Input Synchronization | Live property editing in Node Inspector panel |
| | `TS-5.3` | Managed Pipeline Node Locking | Disabling individual node deletion for infrastructure nodes |

---

## 1. Preset Topology Scenarios

### TS-1.1: Standard Local Pipeline
- **Goal**: Verify loading of the default local offline environment.
- **Action**: Click the **"Load Local Setup"** button in the top action bar.
- **Node Execution Configurations & Probes**:
  1. **Pubber (Device Emulator)**:
     - **Run Config**: `out/pubber_config.json` (site: `sites/udmi_site_model`, deviceId: `AHU-1`, serialNo: `SN-10492`)
     - **Run Command**: `UDMI_NO_SUDO=true bin/pubber sites/udmi_site_model //mqtt/localhost AHU-1 SN-10492`
     - **Health Probe**: `UDMI_NO_SUDO=true bin/pull_mqtt //mqtt/localhost` (monitor state on `/r/udmi-registry/d/AHU-1/state`)
  2. **Local Mosquitto Broker**:
     - **Run Config**: `var/mosquitto/mosquitto.conf` & `var/mosquitto/conf.d/udmi.conf`
     - **Run Command**: `UDMI_NO_SUDO=true MQTT_PORT=18883 bin/start_mosquitto`
     - **Health Probe**: `mosquitto_sub -p 18883 -t "$SYS/broker/uptime" -C 1`
  3. **Local UDMIS (Reflective Core)**:
     - **Run Config**: `var/local_pod.json` (extends `udmis/etc/prod_pod.json`)
     - **Run Command**: `UDMI_NO_SUDO=true bin/start_udmis`
     - **Health Probe**: `test -f var/pod_ready.txt` & sentinel log audit in `out/udmis.log`
- **Expected Outcome**:
  - Canvas renders 3 nodes: `Device Emulator (Pubber)`, `Local Mosquitto Broker` (port `18883`), and `Local UDMIS`.
  - Directed SVG arrows route: `Pubber` $\xrightarrow{\text{Telemetry/State}}$ `Local Mosquitto` $\xrightarrow{\text{Reflective Sync}}$ `Local UDMIS`.
  - Inspector displays executable commands, config locations, and health probes for each selected node.
  - Completeness Chip displays green 🟢 **`COMPLETE SETUP`**.

### TS-1.2: Standard Cloud Pipeline
- **Goal**: Verify loading of the cloud production pipeline.
- **Action**: Click the **"Cloud Setup"** button in the top action bar.
- **Node Execution Configurations & Probes**:
  1. **Pubber (Device Emulator)**:
     - **Run Config**: `out/pubber_config.json` (endpoint: `bos-platform-dev.corp.goog:8883`)
     - **Run Command**: `UDMI_NO_SUDO=true bin/pubber sites/udmi_site_model //pubsub/bos-platform-dev AHU-1 SN-10492`
     - **Health Probe**: `bin/pull_mqtt //pubsub/bos-platform-dev`
  2. **Zanzara Ingress (Auth Proxy)**:
     - **Run Config**: `k8s/dev/auth/ (namespace: udmis)`
     - **Run Command**: `kubectl get deployment auth -n udmis`
     - **Health Probe**: `nc -zv bos-platform-dev.corp.goog 8883`
  3. **Zanzara Message Fabric (Mosquitto & Bridges)**:
     - **Run Config**: `k8s/dev/bridge/`
     - **Run Command**: `kubectl get statefulset -l app=bridge -n udmis`
     - **Health Probe**: `gcloud pubsub topics list --project=bos-platform-dev`
  4. **Cloud UDMIS (Pub/Sub Core)**:
     - **Run Config**: `udmis/etc/prod_pod.json` (GCP Pub/Sub provider)
     - **Run Command**: `kubectl get deployment udmis -n udmis`
     - **Health Probe**: `gcloud pubsub subscriptions pull udmi_target-udmis --auto-ack --limit=1`
- **Expected Outcome**:
  - Canvas renders 5 nodes: `Device Emulator (Pubber)`, `Zanzara Ingress (Auth Proxy)`, `Zanzara Message Fabric`, `Cloud UDMIS (Pub/Sub)`, and `etcd State Store`.
  - Directed SVG arrows route: `Pubber` $\rightarrow$ `Zanzara Ingress` $\rightarrow$ `Zanzara Fabric` $\rightarrow$ `Cloud UDMIS` $\rightarrow$ `etcd`.
  - Completeness Chip displays green 🟢 **`COMPLETE SETUP (CLOUD)`**.

### TS-1.3: Physical Hardware Pipeline
- **Goal**: Verify topology for physical building controllers or BACnet/IP gateways.
- **Action**: Drag **"Actual Device"** from palette, connect to **"Local Mosquitto Broker"** and **"Local UDMIS"**.
- **Node Execution Configurations & Probes**:
  1. **Actual Device (Physical Hardware)**:
     - **Run Config**: `sites/udmi_site_model/devices/AHU-22/metadata.json`
     - **Provisioning Command**: `UDMI_NO_SUDO=true bin/registrar sites/udmi_site_model //mqtt/localhost AHU-22`
     - **Health Probe**: `UDMI_NO_SUDO=true bin/pull_mqtt //mqtt/localhost` (heartbeat on `/r/+/d/AHU-22/state`)
- **Expected Outcome**:
  - Inspector displays configurable parameters (`device_id: AHU-22`, `address: 192.168.1.105`, `protocol: BACnet/IP`).
  - Arrow routes: `Actual Device` $\rightarrow$ `Local Mosquitto Broker` $\rightarrow$ `Local UDMIS`.
  - Completeness Chip displays green 🟢 **`COMPLETE SETUP`**.

---

## 2. Hybrid & Cross-Setup Scenarios

### TS-2.1: Local Broker with Cloud Core
- **Goal**: Validate local telemetry collection bridged to cloud processing.
- **Action**: Instantiate `Pubber` + `Local Mosquitto Broker` + `Cloud UDMIS (Pub/Sub)`.
- **Node Execution Configurations & Probes**:
  1. **Local Mosquitto Broker**: `UDMI_NO_SUDO=true MQTT_PORT=18883 bin/start_mosquitto`
  2. **MQTT-to-PubSub Bridge**: `java -cp udmis/build/libs/udmis-1.0-SNAPSHOT-all.jar com.google.bos.udmi.service.bridge.MqttToPubSubBridge --mqtt_broker_url=tcp://localhost:18883 --pubsub_topic_id=udmi_target`
  3. **Cloud UDMIS**: `gcloud pubsub subscriptions pull udmi_target-udmis`
- **Expected Outcome**:
  - Edges route `Pubber` $\rightarrow$ `Local Mosquitto` $\rightarrow$ `Cloud UDMIS`.
  - Inspector for `Local Mosquitto` shows local port (`18883`), while `Cloud UDMIS` shows GCP Pub/Sub topic.
  - Completeness Chip displays green 🟢 **`COMPLETE SETUP`**.

### TS-2.2: Cloud Ingress with Local Core
- **Goal**: Validate cloud-connected physical hardware managed by a local test engine.
- **Action**: Instantiate `Actual Device` + `Zanzara Ingress` + `Local UDMIS`.
- **Node Execution Configurations & Probes**:
  1. **Zanzara Ingress**: `nc -zv bos-platform-dev.corp.goog 8883`
  2. **Local UDMIS**: `UDMI_NO_SUDO=true bin/start_udmis`
- **Expected Outcome**:
  - Edges route `Actual Device` $\rightarrow$ `Zanzara Ingress` $\rightarrow$ `Local UDMIS`.
  - Inspector for `Zanzara Ingress` shows endpoint and namespace.
  - Completeness Chip displays green 🟢 **`COMPLETE SETUP`**.

---

## 3. Conflict & Invalid Topology Validation Scenarios

### TS-3.1: Dual Broker Conflict
- **Goal**: Verify detection of conflicting message broker endpoints.
- **Action**: Add both `Local Mosquitto Broker` AND `ClearBlade IoT Broker` to the canvas simultaneously.
- **Expected Outcome**:
  - Completeness Chip turns red/amber: ⚠️ **`CONFLICT: Multiple Brokers selected (2)`**.
  - System prevents testbed execution until one broker is removed.

### TS-3.2: Dual Core Conflict
- **Goal**: Verify detection of conflicting UDMIS core engines.
- **Action**: Add both `Local UDMIS` AND `Cloud UDMIS (Pub/Sub)` to the canvas simultaneously.
- **Expected Outcome**:
  - Completeness Chip turns red/amber: ⚠️ **`CONFLICT: Multiple UDMIS Cores selected (2)`**.
  - System prevents testbed execution to avoid desired state race conditions.

### TS-3.3: Incomplete Pipeline – Missing Broker
- **Goal**: Verify warning when devices attempt to bypass the message broker.
- **Action**: Place `Pubber` and `Local UDMIS` on canvas without any Broker node.
- **Expected Outcome**:
  - Completeness Chip displays: ⚠️ **`INCOMPLETE: Missing Broker`**.
  - Fallback edge draws `Direct Sync` labeled line with warning style.

### TS-3.4: Incomplete Pipeline – Missing Core
- **Goal**: Verify warning when telemetry broker has no processing core attached.
- **Action**: Place `Pubber` and `Local Mosquitto Broker` on canvas without a UDMIS Core node.
- **Expected Outcome**:
  - Completeness Chip displays: ⚠️ **`INCOMPLETE: Missing UDMIS Core`**.

### TS-3.5: Disabled Node Rejection
- **Goal**: Ensure disabled palette items cannot be dropped onto the canvas.
- **Action**: Attempt to drag **"Device Emulator (Spotter)"** or **"Ancillary Test Node"** onto canvas.
- **Expected Outcome**:
  - Drag cursor displays disabled indicator; drop event is rejected; no node card added.

---

## 4. Execution, Health Check & Dynamic Port Scenarios

### TS-4.1: Non-Sudo Dynamic Port Selection
- **Goal**: Verify automatic fallback port binding in `UDMI_NO_SUDO=true` mode.
- **Action**:
  1. Occupy default port `18883` using a mock socket listener.
  2. Execute `UDMI_NO_SUDO=true bin/start_mosquitto`.
- **Expected Outcome**:
  - `start_mosquitto` detects `18883` in use, automatically selects free port `18884`, exports `MQTT_PORT=18884`, and binds Mosquitto successfully.

### TS-4.2: Device Registration Prerequisite
- **Goal**: Verify device credentials and metadata exist before starting Sequencer.
- **Action**: Attempt to run Sequencer tests on an unregistered device ID (`AHU-99`).
- **Expected Outcome**:
  - System flags device as unregistered, prompting execution of `bin/registrar sites/udmi_site_model //mqtt/localhost AHU-99`.

### TS-4.3: Message-Based Health Probe
- **Goal**: Verify health status detection without ICMP pinging.
- **Action**: Launch Pubber and execute `Check Node Health` button.
- **Expected Outcome**:
  - System uses `bin/pull_mqtt` to verify message ingestion on `/r/{registry_id}/d/{device_id}/state`.
  - Node status transitions from `INITIALIZING` to `UP`.

---

## 5. UI & Canvas Interaction Scenarios

### TS-5.1: Smooth Dragging & Offset
- **Goal**: Confirm node selection and movement do not cause position jumping.
- **Action**: Click a node card on the canvas and drag it 150px to the right.
- **Expected Outcome**:
  - Card stays under mouse cursor throughout drag movement; does not jump to `(0, 0)` top-left corner.

### TS-5.2: Inspector Input Synchronization
- **Goal**: Verify inspector form edits dynamically update the canvas node card.
- **Action**: Select `Pubber` node card; change `device_id` in Inspector side panel to `AHU-50`.
- **Expected Outcome**:
  - Canvas node card subtext immediately updates to `Dev: AHU-50`.

### TS-5.3: Site Model Context Switching
- **Goal**: Verify switching the top bar `#site-input` re-evaluates topology.
- **Action**: Update top bar `#site-input` path to `sites/udmi_site_model`.
- **Expected Outcome**:
  - Top bar input border turns green; canvas nodes re-run health checks against newly selected site model context.
