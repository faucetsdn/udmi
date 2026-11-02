[**UDMI**](./) / [Fix Discovery](#)

# Fix `Discovery Tests` CI Failure (`udmis` Container vs `tmux_butler`)

## Purpose of This Document
This document captures the root-cause diagnosis, historical context, test topology, and proposed architectural solutions for the `Discovery Tests` CI failure on the `flaky` branch (PR [#1451](https://github.com/faucetsdn/udmi/pull/1451), commit `66d76fec5`, [GitHub Actions run 39536049757 / job 119013686490](https://github.com/faucetsdn/udmi/actions/runs/39536049757/job/119013686490)).

It is written as a self-contained briefing for a future agent session (`@FIX_DISCOVERY.md`). Remember to audit `GEMINI.md` and `AGENTS.md` before implementing and verifying changes.

---

## 1. Failure Summary & Technical Signature

On commit `66d76fec5`, 30 of 31 CI checks passed; only **`Discovery Tests`** failed during the **`E2E Tests`** step (`misc/discoverynode/testing/e2e/test_local site_model`).

### Exact Sequence of Events in CI
1. **`.github/workflows/testing.yml` (`Start UDMIS container`, lines 534–576)** launches the `udmis` Docker container with `bin/udmi start` as PID 1:
   ```bash
   docker run -d --net udminet --name udmis -p 8883:8883 -p 18836:5432 \
     -v $PWD/site_model:/root/site_model \
     -v $PWD/var/tmp:/tmp \
     -v $PWD/var/etcd:/root/udmi/var/etcd \
     -v $PWD/var/mosquitto:/etc/mosquitto \
     -v $PWD/out:/root/udmi/out \
     udmis udmi/bin/udmi start block site_model //mqtt/localhost:8883
   ```
2. **Phase 1 inside the container (`bin/tmux_barbican`) succeeds**:
   - Starts `etcd`, `mosquitto`, and `udmis` inside the `udmi_barbican` tmux session.
   - `udmis` initializes and writes `/tmp/pod_ready.txt` (mounted to `var/tmp/pod_ready.txt` on the host).
   - The CI loop in `Start UDMIS container` sees `var/tmp/pod_ready.txt` on the host and exits `0` immediately.
3. **Phase 2 inside the container (`bin/tmux_butler`) fails and kills PID 1**:
   - Immediately after `tmux_barbican` returns, `bin/udmi` runs `bin/tmux_butler start site_model //mqtt/localhost:8883`.
   - `tmux_butler` spawns tmux windows for `postgres`, `influxdb`, `butler`, and `registrar`:
     - **`influxdb` window fails**: `udmis/Dockerfile.udmis` does not install `influxd`, so `bin/start_influx` exits with code 1 (`Unable to find influxd binary in /usr/bin, /usr/local/bin, or /tmp/influxdb`).
     - **`registrar` window fails**: `bin/registrar` invokes `validator/bin/registrar` -> `bin/toolrun registrar com.google.daq.mqtt.util.Dispatcher ...`. Because `udmis/Dockerfile.udmis` does not package `validator/` or `validator/build/libs/validator-1.0-SNAPSHOT-all.jar`, `toolrun` attempts to run `validator/bin/build`, fails, and `java` crashes:
       ```text
       Building validator/build/libs/validator-1.0-SNAPSHOT-all.jar...
       ./bin/registrar: line 15: validator/bin/build: No such file or directory
       Running tools version 1.5.2-403-g66d76fec5
       Error: Could not find or load main class com.google.daq.mqtt.util.Dispatcher
       Caused by: java.lang.ClassNotFoundException: com.google.daq.mqtt.util.Dispatcher
       === [registrar] exited with code 1 ===
       ```
   - `bin/tmux_butler` (lines 75–96) detects `=== [registrar] exited with code 1 ===` and calls `fail "Initial registrar run in 'udmi_butler' failed."`.
   - Because `bin/udmi` runs with `#!/bin/bash -e` as PID 1 of the container, `bin/udmi` exits with code 1 before reaching `tail -f /dev/null` on line 304, **terminating the entire `udmis` container**.
4. **The `E2E Tests` step (`misc/discoverynode/testing/e2e/test_local site_model`) fails on the host**:
   - `docker exec udmis psql -h 127.0.0.1 -p 5432 -U postgres -d postgres -c "TRUNCATE TABLE udmi_messages;"` fails with:
     ```text
     Error response from daemon: container ... is not running
     ```
   - `bin/registrar` and `bin/mapper` on the host fail to connect to Mosquitto (`127.0.0.1:8883`) and PostgreSQL (`127.0.0.1:18836`) with `ConnectionRefusedError: [Errno 111] Connection refused`.
   - All 3 `pytest` tests in `misc/discoverynode/testing/e2e/test_local.py` fail.

---

## 2. What `Discovery Tests` Is Testing & How Its Topology Works

The `Discovery Tests` CI job (`.github/workflows/testing.yml:497-598`) tests **`misc/discoverynode`** (the Python edge agent / "Spotter" that scans BACnet, Ethernet/nmap, passive IPv4, and vendor networks and publishes UDMI discovery events).

### The 3 Test Stages in `Discovery Tests`
1. **Unit Tests (`misc/discoverynode/testing/unit/unit_tests.sh`)**:
   Runs isolated `pytest` unit tests inside the `test-discovery_node` container.
2. **Integration Tests (`misc/discoverynode/testing/integration/integration_test.sh` & `misc/discoverynode/src/tests/test_integration.py`)**:
   Spins up a Docker network (`discovery-integration`, `192.168.12.0/24`) with a simulated BACnet device container (`test-bacnet-device` at `192.168.12.1`) and runs `test-discovery_node` with a mocked MQTT client to verify real BACnet UDP broadcast discovery (`Who-Is`/`I-Am`, `refs`) and `nmap` SSH discovery (`OpenSSH`).
3. **E2E Tests (`misc/discoverynode/testing/e2e/test_local` & `test_local.py`)**:
   Tests the full end-to-end closed-loop UDMI discovery and mapping workflow across 3 `pytest` cases:
   - `test_discovered_proxied_devices_are_created` (discovers and maps 9 BACnet devices behind `GAT-1`)
   - `test_discovered_devices_are_created` (discovers and maps 6 BACnet devices behind `AHU-1`)
   - `test_sequencer` (runs `bin/sequencer -v site_model //mqtt/localhost GAT-1 scan_single_future`)

### E2E Network & Process Topology
```text
┌────────────────────────────────────────────────────────────────────────────┐
│ CI Host (ubuntu-24.04)                                                     │
│  - Runs pytest (`misc/discoverynode/testing/e2e/test_local.py`)            │
│  - Runs `bin/registrar`, `bin/mapper`, `bin/sequencer` against:            │
│      • MQTT:       localhost:8883  (forwarded to udmis container :8883)    │
│      • PostgreSQL: localhost:18836 (forwarded to udmis container :5432)    │
└───────────────┬────────────────────────────────────────┬───────────────────┘
                │ -p 8883:8883                           │
                │ -p 18836:5432                          │
                ▼                                        ▼
┌──────────────────────────────────────┐   ┌─────────────────────────────────┐
│ Docker net: `udminet`                │   │ Docker net:                     │
│ (192.168.99.0/24)                    │   │ `discoverynode-network`         │
│                                      │   │ (192.168.11.0/24)               │
│  ┌────────────────────────────────┐  │   │                                 │
│  │ Container: `udmis`             │  │   │  ┌───────────────────────────┐  │
│  │ (192.168.99.2)                 │  │   │  │ `discoverynode-test-      │  │
│  │  - mosquitto (:8883)           │  │   │  │  device1..9`              │  │
│  │  - etcd (:2379)                │  │   │  │ (192.168.11.1..9,         │  │
│  │  - udmis Java pod              │  │   │  │  BACnet IDs 3001..3009)   │  │
│  │  - postgres (:5432)            │  │   │  └─────────────▲─────────────┘  │
│  │  - butler daemon               │  │   │                │ BACnet UDP     │
│  └───────────────▲────────────────┘  │   │                ▼                │
│                  │ MQTT (:8883)      │   │  ┌───────────────────────────┐  │
│                  └───────────────────┼───┼──┤ `discoverynode-test-node` │  │
│                     (dual-homed)     │   │  │ (192.168.11.251)          │  │
└──────────────────────────────────────┘   └──┴───────────────────────────┴──┘
```

### Closed-Loop Data Flow in `test_local.py`
1. `test_local.py` generates a site model on the host (`site_model/devices/GAT-1`) and runs `bin/registrar` on the host to register `GAT-1` into Mosquitto/etcd (`localhost:8883`).
2. `test_local.py` starts `discoverynode-test-device1..9` on `discoverynode-network` and `discoverynode-test-node` on both `discoverynode-network` and `udminet` (configured to connect to MQTT at `192.168.99.2:8883`).
3. `test_local.py` runs `bin/mapper site_model //mqtt/localhost GAT-1 discover` on the host, publishing a discovery scan config to `GAT-1`.
4. `discoverynode-test-node` receives the config over MQTT, runs a BACnet scan on `192.168.11.0/24`, discovers the BACnet device containers, and publishes `discovery` events to Mosquitto (`192.168.99.2:8883`).
5. `udmis` reflects those messages to the `/udmi` MQTT feed (`rocket:monkey`), where the `butler` daemon (`bin/start_butler`) ingests them into PostgreSQL (`udmi_messages` / `udmi_discovery` tables).
6. `test_local.py` runs `bin/mapper site_model //mqtt/localhost GAT-1 map bacnet` on the host, which queries PostgreSQL (`POSTGRES_PORT=18836`) and writes discovered device models, followed by `bin/registrar` on the host, which populates `site_model/extras/00001..00009`.

---

## 3. Historical Context: Why `butler` Is Inside the `udmis` Container

1. **Before PR #1422**:
   The `udmis` container was a "Barbican-in-a-box" appliance (`etcd`, `mosquitto`, `udmis`) started with `udmi/bin/start_local block site_model/cloud_iot_config.json`.
2. **PR #1422 (`88249286e`, *"Updating mapping flow to new UUFI python utility"*)**:
   Replaced the Java `MappingAgent` with Python `bin/mapper` (`butler/bin/butler`). Because `bin/mapper ... map` queries PostgreSQL (`udmi_messages`) instead of listening to MQTT directly, PostgreSQL was added inside the `udmis` container and port-forwarded to the host (`-p 18836:5432` in `.github/workflows/testing.yml`, `POSTGRES_PORT: 18836` in `E2E Tests`, and `docker exec udmis psql ... TRUNCATE TABLE udmi_messages;` in `test_local.py:379`).
3. **PR #1425 (`6d35b7541`, *"Migrate database capture code to butler, in python"*)**:
   Removed `CaptureProcessor.java` from `UdmiServicePod` and replaced it with the Python `butler` ingestion daemon (`bin/start_butler`). To keep the `udmis` container self-contained for `Discovery Tests` (and `bridgehead`), `butler/`, `common/`, `gencode/`, and `schema/` were added to `udmis/Dockerfile.udmis` and `udmis/bin/build`, and `bin/start_butler` was added to `bin/start_local` (and `bridgehead/udmis/startup/udmis_startup.sh`).
4. **PR #1427 (`c5ed8f302`, *"Updates to the tmux process control model"*)**:
   Replaced `bin/start_local` with `bin/udmi start`, which splits local services into two tmux sessions (documented in `ARCHITECTURE.md`):
   - **Barbican (`bin/tmux_barbican`)**: `etcd`, `mosquitto`, `udmis`, `pubsub`
   - **Butler (`bin/tmux_butler`)**: `postgres`, `influxdb`, `butler`, `registrar`, `validator`
   Because `bin/udmi start` launches **both** `tmux_barbican` and `tmux_butler` by default, switching the `udmis` container command in `Discovery Tests` from `bin/start_local` to `udmi/bin/udmi start block site_model //mqtt/localhost:8883` caused the `udmis` container to run `tmux_butler` as well.
5. **Current `flaky` Branch (`4e7f1be3d` & `66d76fec5`)**:
   `registrar` became default-enabled in `bin/tmux_butler` with synchronous exit-status verification (`etc/tmux_common.sh` + `bin/tmux_butler`). Because `udmis/Dockerfile.udmis` does not contain `validator/build/libs/validator-1.0-SNAPSHOT-all.jar`, `tmux_butler` now fails deterministically inside the `udmis` container.

---

## 4. Proposed Solutions

There are two main architectural approaches to resolve this cleanly. **Discuss with the user or select the approach that aligns with their preferred container boundary before editing.**

### Option 1: Take `tmux_butler` Out of the `udmis` Container (Clean Barbican / Butler Separation)
Make the `udmis` container run **only Barbican** (`etcd`, `mosquitto`, `udmis`), matching `ARCHITECTURE.md`, and run `tmux_butler` on the CI host in `Discovery Tests`.

- **Why this makes architectural sense**:
  - In `ARCHITECTURE.md`, `udmis` belongs to **Barbican** (`bin/tmux_barbican`), while `postgres`, `influxdb`, `butler`, `registrar`, and `validator` belong to **Butler** (`bin/tmux_butler`).
  - `udmis/Dockerfile.udmis` does not have `validator-1.0-SNAPSHOT-all.jar` (needed by `registrar` and `validator`) or `influxd` (needed by `influxdb`).
  - On the CI host in `Discovery Tests`, `sudo bin/setup_base` and `udmi-prebuilt-packages` are **already installed**, and `test_local.py` already runs `bin/registrar`, `bin/mapper` (`butler`), and `bin/sequencer` on the host.
- **What changes under Option 1**:
  1. **Container startup command**:
     Have the `udmis` container run only Barbican—either by invoking `udmi/bin/tmux_barbican` or by scoping `bin/udmi start` inside the container (e.g., `udmi/bin/udmi start block site_model //mqtt/localhost:8883 +mosquitto +etcd +udmis`, or having `bin/udmi` skip `tmux_butler` when running inside the `udmis` container / without `validator`).
     *(Check `.github/workflows/testing.yml:543`, `docs/udmis/local_docker.md:34`, and `udmis/bin/actualize:14`.)*
  2. **Host Butler startup in `.github/workflows/testing.yml` (`Discovery Tests`)**:
     - Remove `-p 18836:5432` from `docker run ... --name udmis` (`.github/workflows/testing.yml:537`) and remove `POSTGRES_PORT: 18836` from `E2E Tests` (`.github/workflows/testing.yml:585`).
     - Start `tmux_butler` on the host after the `udmis` container is ready (e.g., `bin/tmux_butler start site_model //mqtt/localhost:8883 !registrar` or after generating initial certs, since `test_local.py` wipes and recreates `site_model/devices` in its `new_site_model` fixture).
  3. **`misc/discoverynode/testing/e2e/test_local.py:379`**:
     Change:
     ```python
     run("docker exec udmis psql -h 127.0.0.1 -p 5432 -U postgres -d postgres -c \"TRUNCATE TABLE udmi_messages;\"")
     ```
     to run `psql` directly against the host PostgreSQL instance:
     ```python
     run("psql -h 127.0.0.1 -p 5432 -U postgres -d postgres -c \"TRUNCATE TABLE udmi_messages;\"")
     ```
  4. **Caveat for `bridgehead`**:
     `bridgehead/udmis/Dockerfile` builds `FROM ${UDMIS_BASE_IMAGE}` (`udmis:latest`) and `bridgehead/udmis/startup/udmis_startup.sh:92` explicitly runs `$UDMI_ROOT/bin/start_butler "$site_model" "$project_spec"` (talking to separate `postgres` and `influxdb` containers in `bridgehead/docker-compose.yml`). Therefore, keep the `ADD build/butler/ udmi/butler/` (and `common`, `gencode`, `schema`) lines in `udmis/Dockerfile.udmis` even if `tmux_butler` is not run inside the standalone `udmis` container.

### Option 2: Keep `postgres` + `butler` in the `udmis` Container, Exclude `!registrar` and `!influxdb`
If the standalone `udmis` container is intended to remain a self-contained appliance for `mosquitto` + `etcd` + `udmis` + `postgres` + `butler` without requiring `validator` or `influxd` inside the image:

- **What changes under Option 2**:
  1. Exclude `registrar` and `influxdb` when starting `bin/udmi` in the `udmis` container:
     - In `.github/workflows/testing.yml:543`, `docs/udmis/local_docker.md:34`, and `udmis/bin/actualize:14`:
       ```bash
       udmis udmi/bin/udmi start block site_model //mqtt/localhost:8883 !registrar !influxdb
       ```
     - Or in `bin/udmi` / `bin/tmux_butler`, do not default-enable `registrar` (or `influxdb`) when running inside the `udmis` container environment where `validator` / `influxd` are not installed (though remember `AGENTS.md`: *"Fail Fast and No Silent Fallbacks"* — explicit service filters `!registrar !influxdb` are cleaner than silent environment sniffing).
  2. No changes needed to `-p 18836:5432`, `POSTGRES_PORT: 18836`, or `test_local.py:379`, because `postgres` and `butler` continue running inside the `udmis` container and `test_local.py` already runs `bin/registrar` on the host before each test.

---

## 5. Key Files Reference

| File | Lines | Role |
| :--- | :--- | :--- |
| `.github/workflows/testing.yml` | `497–598` | `Discovery Tests` CI job definition (`Build UDMIS container`, `Start UDMIS container`, `E2E Tests`) |
| `bin/udmi` | `214–304` | Orchestrates `bin/tmux_barbican` and `bin/tmux_butler`, waits for `POD_READY`, then blocks if `block` is passed |
| `bin/tmux_barbican` | `1–102` | Starts `etcd`, `mosquitto`, `udmis`, and optional `pubsub` |
| `bin/tmux_butler` | `1–109` | Starts `postgres`, `influxdb`, `butler`, `registrar`, and optional `validator`; waits synchronously for `registrar` exit `0` |
| `etc/tmux_common.sh` | `195–218` | `tmux_init_or_add_window` wrapper that logs `=== [<win_name>] exited with code $ec ===` |
| `udmis/Dockerfile.udmis` | `1–39` | Builds the `udmis` image (has `udmis` JAR, `butler/`, `postgresql`, `mosquitto`, `tmux`; does **not** have `validator` JAR or `influxd`) |
| `udmis/bin/build` | `45–54` | Mirrors `bin/`, `etc/`, `schema/`, `butler/`, `common/`, `gencode/`, `mcp/` into `udmis/build/` |
| `misc/discoverynode/testing/e2e/test_local` | `1–25` | Wrapper script invoked by `Discovery Tests` (`E2E Tests` step) |
| `misc/discoverynode/testing/e2e/test_local.py` | `234–380` | `pytest` E2E tests (`test_discovered_proxied_devices_are_created`, `test_discovered_devices_are_created`, `test_sequencer`, and `new_site_model` fixture with `docker exec udmis psql ...`) |
| `bridgehead/udmis/startup/udmis_startup.sh` | `91–92` | Starts `bin/start_butler` inside Bridgehead's `udmis` container |
| `docs/udmis/local_docker.md` | `29–35` | Documentation for running the `udmis` container locally |
| `udmis/bin/actualize` | `9–15` | Helper script to bootstrap the `udmis` container |
