# UDMI Workbench Testbed Node Specifications

This document defines the detailed run configurations, execution commands, and health check signatures for each node type supported in the UDMI Testbed interactive workspace.

---

## 1. Pubber (Device Emulator)

### A. Run Configuration (`pubber_config.json`)
Pubber requires a JSON configuration file containing site model paths, target device identifiers, MQTT connection parameters, and runtime options:

```json
{
  "sitePath": "sites/udmi_site_model",
  "deviceId": "AHU-1",
  "registryId": "udmi-registry",
  "iotProject": "//mqtt/localhost",
  "serialNo": "SN-10492",
  "endpoint": {
    "protocol": "mqtt",
    "transport": "ssl",
    "hostname": "localhost",
    "port": 1883,
    "client_id": "/r/udmi-registry/d/AHU-1",
    "auth_provider": {
      "basic": {
        "username": "/r/udmi-registry/d/AHU-1",
        "password": "<hash>"
      }
    }
  },
  "options": {
    "noPersist": true,
    "interval_sec": 10
  }
}
```

**Key Parameters**:
- `sitePath`: Path to the active site model directory containing `cloud_iot_config.json` and `devices/`.
- `deviceId`: Target device identifier (e.g. `AHU-1`).
- `serialNo`: Emulator serial number (`SN-10492`).
- `endpoint`: Host, port, TLS cert paths, or basic auth credentials for the target MQTT broker.
- `options`: Behavioral flags (e.g. `noPersist: true` to prevent state caching between test runs).

---

### B. Run Commands

- **High-Level Shell Wrapper** (`bin/pubber`):
  ```bash
  bin/pubber sites/udmi_site_model //mqtt/localhost AHU-1 SN-10492
  ```

- **Direct Java Execution**:
  ```bash
  java -Dorg.slf4j.simpleLogger.showThreadName=false \
       --add-opens=java.base/java.net=ALL-UNNAMED \
       -jar pubber/build/libs/pubber-1.0-SNAPSHOT-all.jar \
       out/pubber_config.json
  ```

---

### C. Health Checks & Diagnostic Signals

1. **Process Liveness**:
   - Check process table for active execution:
     ```bash
     pgrep -f "pubber-1.0-SNAPSHOT-all.jar"
     ```

2. **Log Signature Monitoring**:
   - Log location: `out/pubber.log.<device_id>` or `pubber/out/<serial_no>/pubber.log`.
   - **🟢 Healthy Up Signatures**:
     - `"Pubber main started"`
     - `"MQTT Connection complete"`
     - `"Publishing telemetry for AHU-1"`
     - `"Received config update"`
   - **🔴 Failure / Down Signatures**:
     - `"Connection lost / MqttException"`
     - `"Key error loading private key"`
     - `"Site model path invalid"`

3. **MQTT Boundary Probing**:
   - Verify active message publication by subscribing to the broker endpoint:
     ```bash
     mosquitto_sub -h localhost -p 1883 -t "/r/udmi-registry/d/AHU-1/events/#" -v
     ```

---

## 2. Actual Device (Physical Hardware / Gateways)

### A. Run Configuration (`metadata.json` & Network Inputs)
An actual physical device (e.g., AHU controller, Modbus/BACnet gateway, smart meter) does not run inside the local Workbench process. Instead, its configuration is defined in the Site Model under `devices/{device_id}/metadata.json` and key pairs:

```json
{
  "system": {
    "location": {
      "site_name": "US-BUILDING-10"
    },
    "hardware": {
      "make": "Tridium / Honeywell",
      "model": "JACE-8000"
    }
  },
  "gateway": {
    "protocol": "BACnet/IP",
    "address": "192.168.1.105"
  }
}
```

**Inspector Inputs**:
- `device_id`: Device identifier matching `devices/{device_id}` in site model (e.g., `AHU-22`).
- `address`: Network IP address or hostname (`192.168.1.105`).
- `protocol`: Industrial communications protocol (`BACnet/IP`, `Modbus-RTU`, `EnOcean`, `Direct-MQTT`).

---

### B. Execution & Provisioning Prerequisites

> [!IMPORTANT]
> **Device Registration Prerequisite**:
> A device (whether **Pubber** or an **Actual Device**) **MUST be registered first** in the Site Model and target registry before running Sequencer tests.
> The MQTT broker authenticates devices via client ID endpoints (`/r/{registry_id}/d/{device_id}`) and public key certificates. Unregistered devices or missing keys cause connection rejection.
> Run `bin/registrar` or key generation before initiating test sequences:
> ```bash
> UDMI_NO_SUDO=true bin/registrar sites/udmi_site_model //mqtt/localhost AHU-22
> ```

- **Site Model Key Distribution & Registration**:
  ```bash
  UDMI_NO_SUDO=true bin/keygen CERT/gcp-project sites/udmi_site_model/devices/AHU-22/
  ```

---

### C. Message-Based Health Checks & Diagnostic Signals

> [!NOTE]
> **No ICMP Direct Pinging**: Direct network ICMP pinging is not used because building network firewalls frequently block ICMP, and UDMI operates strictly over message protocols. Device health is evaluated exclusively through live MQTT/PubSub message ingestion.

1. **Live MQTT Message Capture (`bin/pull_mqtt`)**:
   - Use the built-in message capturer to listen for live device telemetry and state updates without root privileges:
     ```bash
     UDMI_NO_SUDO=true bin/pull_mqtt //mqtt/localhost
     ```
   - Messages are automatically captured and categorized under `out/registries/{registry_id}/devices/{device_id}/`.

2. **MQTT Telemetry Heartbeat Verification**:
   - Direct topic subscription for live state heartbeats (`devices/{device_id}/state` or `/r/{registry_id}/d/{device_id}/state`):
     ```bash
     mosquitto_sub -h localhost -p 1883 -t "/r/+/d/AHU-22/#" -v
     ```

3. **Status Indicators**:
   - **🟢 UP**: Active state/telemetry heartbeat messages received on the broker within target interval (`< 60s`) with valid schema headers.
   - **🟡 UNINITIALIZED**: Device registered in Site Model, but no live state/telemetry message captured yet on the broker.
   - **🔴 DOWN**: No state/telemetry messages received after timeout, or connection authentication rejected by broker.

---

## 3. Local Mosquitto Broker

### A. Run Configuration (`etc/mosquitto.conf` & `etc/conf.d/udmi.conf`)
The local MQTT message broker runs Eclipse Mosquitto with the `mosquitto_dynamic_security` plugin enabled. Configuration files are stored in `var/mosquitto/`:

```ini
# var/mosquitto/mosquitto.conf
pid_file var/mosquitto/mosquitto.pid
persistence true
persistence_location var/mosquitto/data/
log_dest file var/mosquitto/log/mosquitto.log
include_dir var/mosquitto/conf.d/
```

```ini
# var/mosquitto/conf.d/udmi.conf
listener 18883
plugin /usr/lib/x86_64-linux-gnu/mosquitto_dynamic_security.so
plugin_opt_config_file var/mosquitto/dynamic_security.json
password_file var/mosquitto/mosquitto.passwd
```

**Inspector Inputs & Dynamic Port Allocation**:
- `port`: Default non-sudo unprivileged port (`18883`).
- **Automatic Dynamic Port Fallback**: When running in non-sudo mode (`UDMI_NO_SUDO=true`), if port `18883` is occupied by another process, `bin/start_mosquitto` automatically probes and binds to the next available free port in the range `18883`–`18899` and exports `MQTT_PORT`.
- `use_tls`: Boolean flag for TLS certificate enforcement (`false` for local dev, `true` for production TLS).

---

### B. Run Commands

- **High-Level Shell Wrapper** (`bin/start_mosquitto`):
  ```bash
  UDMI_NO_SUDO=true bin/start_mosquitto
  ```

- **Direct Process Execution**:
  ```bash
  mosquitto -c var/mosquitto/mosquitto.conf
  ```

---

### C. Health Checks & Diagnostic Signals

1. **Process & Dynamic Port Probe**:
   - Check process table and active port binding (`${MQTT_PORT:-18883}`):
     ```bash
     pgrep -x mosquitto
     ss -tulpn | grep "${MQTT_PORT:-18883}"
     ```

2. **System Topic Telemetry Probe**:
   - Subscribe to Mosquitto system metrics topic (`$SYS/broker/uptime`):
     ```bash
     mosquitto_sub -h localhost -p "${MQTT_PORT:-18883}" -t "$SYS/broker/uptime" -C 1
     ```

3. **Status Indicators**:
   - **🟢 UP**: Mosquitto process active, bound non-sudo port (`18883` or dynamically selected fallback) listening, `$SYS/broker/uptime` responding.
   - **🟡 INITIALIZING**: Mosquitto daemon starting up, loading dynamic security ACL rules (`dynsec`).
   - **🔴 DOWN**: Bound port closed, process terminated (`Address already in use` or missing `mosquitto_dynamic_security.so`).

---

## 4. Local UDMIS (Reflective Integration Core)

### A. Run Configuration (`var/local_pod.json`)
Local UDMIS executes the reflective engine using a local pod configuration (`var/local_pod.json`), which extends `udmis/etc/prod_pod.json` and overrides endpoint parameters for local execution:

```json
{
  "include": "udmis/etc/prod_pod.json",
  "flow_defaults": {
    "ca_file": "var/mosquitto/certs/ca.crt",
    "cert_file": "var/mosquitto/certs/rsa_private.crt",
    "key_file": "var/mosquitto/certs/rsa_private.pem",
    "port": 18883
  },
  "iot_access": {
    "implicit": {
      "project_id": "http://127.0.0.1:2379",
      "endpoint": {
        "port": 18883,
        "ca_file": "var/mosquitto/certs/ca.crt",
        "cert_file": "var/mosquitto/certs/rsa_private.crt",
        "key_file": "var/mosquitto/certs/rsa_private.pem"
      }
    }
  }
}
```

**Inspector Inputs**:
- `mode`: Execution mode (`LOCAL` or `CLOUD_PUBSUB`).
- `site_model`: Active site model directory (`sites/udmi_site_model`).

---

### B. Run Commands

- **High-Level Shell Wrapper** (`bin/start_udmis`):
  ```bash
  UDMI_NO_SUDO=true bin/start_udmis
  ```

- **Direct Java Execution**:
  ```bash
  java -XX:-OmitStackTraceInFastThrow \
       -jar udmis/build/libs/udmis-1.0-SNAPSHOT-all.jar \
       var/local_pod.json
  ```

---

### C. Health Checks & Diagnostic Signals

1. **Pod Readiness Sentinel (`var/pod_ready.txt`)**:
   - UDMIS writes a readiness sentinel file (`var/pod_ready.txt`) upon completing message flow initialization.
     ```bash
     test -f var/pod_ready.txt
     ```

2. **Process Liveness**:
   - Check PID recorded in `var/udmis.pid`:
     ```bash
     pgrep -F var/udmis.pid
     ```

3. **Log Signature Monitoring**:
   - Log location: `out/udmis.log`.
   - **🟢 Healthy Up Signatures**:
     - `"Starting UDMIS container"`
     - `"Created message dispatcher"`
     - `"StateProcessor initialized"`
     - `"Pod ready indicator written to var/pod_ready.txt"`
   - **🔴 Failure / Down Signatures**:
     - `"Pod execution exception"`
     - `"ConnectException: Connection refused"`
     - `"Failed to load pod configuration"`

4. **Status Indicators**:
   - **🟢 UP**: Process PID active in OS table, `var/pod_ready.txt` sentinel file present, `StateProcessor` initialized.
   - **🟡 INITIALIZING**: Process PID active, but waiting for `var/pod_ready.txt` creation (startup loop up to 30s).
   - **🔴 DOWN**: `var/udmis.pid` process missing or `var/pod_ready.txt` absent after startup timeout.

---

## 5. Zanzara Ingress (MQTT Auth Proxy)

### A. Run Configuration & Topology Role
In `bos-platform-dev`, `staging`, and `prod`, the **Zanzara Ingress** (`auth` / `auth-canary` deployment) runs the `mqttproxy` container fronted by an internal/external Load Balancer and Private Service Connect (PSC).
It terminates device TLS connections on port `8883`/`1883`, authenticates credentials against the **`etcd`** backend, and proxies authenticated traffic upstream to the internal `mosquitto` broker.

**Inspector Inputs**:
- `endpoint`: Ingress hostname (`bos-platform-dev.corp.goog`).
- `port`: Ingress port (`8883`).
- `namespace`: Kubernetes namespace (`udmis`).
- `project_id`: Target GCP Project ID (`bos-platform-dev`).

---

### B. Run & Diagnostic Commands

- **Check Deployment Status**:
  ```bash
  kubectl get deployment auth -n udmis
  ```

- **mTLS Ingress Port Reachability Probe**:
  ```bash
  nc -zv bos-platform-dev.corp.goog 8883
  ```

---

### C. Health Checks & Diagnostic Signals

1. **TCP / TLS Handshake Probe**:
   - Verify network path and port reachability on port 8883.

2. **Auth Proxy Log Monitoring**:
   - Stream logs to check device authentication events:
     ```bash
     kubectl logs -f -l app=auth -n udmis
     ```
   - **🟢 UP**: TLS handshake successful, device authentication against `etcd` valid, proxy stream active.
   - **🔴 DOWN / AUTH_FAIL**: Port unreachable or authentication rejected (`401 Unauthorized` / certificate mismatch).

---

## 6. Zanzara Message Fabric (Mosquitto & Pub/Sub Bridges)

### A. Run Configuration
The Zanzara Message Fabric consists of the core `mosquitto` broker and dedicated **Bridge StatefulSets** (`bridge-events`, `bridge-state`, `bridge-reflect`) that forward MQTT messages into Google Cloud Pub/Sub topics (`udmi_target`, `udmi_state`, `udmi_reflect`).

**Inspector Inputs**:
- `pubsub_project`: GCP Project hosting Pub/Sub topics (`bos-platform-dev`).
- `namespace`: Kubernetes namespace (`udmis`).
- `topics`: Comma-separated target Pub/Sub topics (`udmi_target, udmi_state, udmi_reflect`).

---

### B. Run & Diagnostic Commands

- **Check Bridge StatefulSets**:
  ```bash
  kubectl get statefulsets -l app=bridge -n udmis
  ```

- **Verify Pub/Sub Topics**:
  ```bash
  gcloud pubsub topics list --project=bos-platform-dev
  ```

---

### C. Health Checks & Diagnostic Signals

1. **Bridge Statefulness**:
   - All bridge pods in `Running` state with 0 crash restarts.

2. **Status Indicators**:
   - **🟢 UP**: Bridge StatefulSets active, subscribing to Mosquitto and publishing to Cloud Pub/Sub without lag.
   - **🔴 DOWN**: Bridge pods terminating, shared subscription failure, or Pub/Sub quota exhaustion.

---

## 7. Cloud UDMIS (Pub/Sub Reflective Core)

### A. Run Configuration (`cloud_pod.json` / `prod_pod.json`)
Cloud UDMIS runs in Kubernetes as `udmis-pods`, consuming telemetry from **GCP Cloud Pub/Sub** subscriptions (`udmi_target-udmis`, etc.) and executing reflective processors (`StateProcessor`, `TargetProcessor`, `ReflectProcessor`).

```json
{
  "include": "udmis/etc/prod_pod.json",
  "flow_defaults": {
    "protocol": "pubsub",
    "hostname": "bos-platform-dev"
  }
}
```

**Inspector Inputs**:
- `topic`: GCP Pub/Sub topic path (`projects/bos-platform-dev/topics/udmi_target`).
- `subscription`: GCP Pub/Sub subscription ID (`udmi_target-udmis`).
- `namespace`: Kubernetes namespace (`udmis`).

---

### B. Run Commands

- **Check Pod Status in Cluster**:
  ```bash
  kubectl get pods -l app=udmis -n udmis
  ```

- **GCP Pub/Sub Subscription Pull Probe**:
  ```bash
  gcloud pubsub subscriptions pull udmi_target-udmis --auto-ack --limit=1
  ```

---

### C. Health Checks & Diagnostic Signals

1. **Pub/Sub Subscription Health Probe**:
   - Query GCP Pub/Sub metrics to confirm subscription message flow:
     ```bash
     gcloud pubsub subscriptions describe udmi_target-udmis --format="json(name,pushConfig)"
     ```

2. **Log Signature Monitoring**:
   - **🟢 Healthy Up Signatures**:
     - `"PubSubPipe initialized for topic projects/..."`
     - `"Subscribed to GCP Pub/Sub subscription udmi_target-udmis"`
     - `"ReflectProcessor processing state update"`
   - **🔴 Failure / Down Signatures**:
     - `"GoogleAuthException: Could not load default credentials"`
     - `"NotFoundException: Resource projects/.../topics/udmi_target not found"`

3. **Status Indicators**:
   - **🟢 UP**: GCP Pub/Sub subscription active, cloud credentials valid, reflective messages processing.
   - **🟡 INITIALIZING**: Connecting to GCP Cloud Pub/Sub API.
   - **🔴 DOWN**: Missing GCP credentials (`GoogleAuthException`), Pub/Sub topic not found, or subscription quota exceeded.




