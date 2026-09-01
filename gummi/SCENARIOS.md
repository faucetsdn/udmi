# **GUMMI Testing Scenarios & Operator Runbook**

## *Author: Trevor Pering, Updated: Aug 23, 2026*

This runbook outlines the standard testing scenarios, verification workflows, and automated test configurations for GUMMI.

---

## **Scenario Matrix**

| # | Scenario | Environment / Mode | Scope | Key Commands |
|---|---|---|---|---|
| **1** | **Standalone Mock UI & API** | In-memory / Mock DB | Fast validation of UI layout, tab navigation, search/filtering, and mock message lifecycle | `bin/gummi --port 8088` |
| **2** | **End-to-End Playwright Testing** | Headless Chromium | Automated browser validation of DOM transitions, zero console errors, inspect flows, and inputs | `venv/bin/pytest gummi/tests/test_ui_playwright.py`<br>`bin/test_gummi` |
| **3** | **Device Discovery & Proposal Lifecycle** | Mock / Live Butler DB | Multi-stage verification of Base Model (`subType: model`) → Discovery (`events/discovery`) → Proposal (`propose` with `updateFrom`) | `POST /api/mapping/run`<br>`gummi/tests/test_mapping.py` |
| **4** | **Managed Rollout Simulation** | Local Broker / Mock | Staged configuration rollout across fleet subsets, batching intervals, pause/cancel controls, and convergence monitoring | `POST /api/rollouts`<br>`POST /api/rollouts/{id}/pause` |
| **5** | **Hermetic Bridgehead Integration** | Mosquitto + Postgres + UUFI | Full pipeline testing connecting GUMMI to a local bridgehead MQTT broker and PostgreSQL `udmi_messages` table | `bin/start_local sites/udmi_site_model //mqtt/localhost:46432`<br>`bin/gummi --mqtt-host localhost --mqtt-port 46432` |
| **6** | **Live Hardware / Pubber Telemetry** | Pubber DUT + Validator | Real-time telemetry point streaming and pointset validation | `bin/test_validator //mqtt/localhost:46432`<br>`bin/test_automapper` |

---

## **Detailed Scenario Procedures**

### **Scenario 1: Standalone Mock UI & API Exploration**

* **Purpose**: Allows UI development and user testing without requiring local database servers or MQTT brokers.
* **Execution**:
  ```bash
  bin/gummi --port 8088
  ```
* **Verification Steps**:
  1. Open [http://localhost:8088](http://localhost:8088).
  2. Verify the **Portfolio Overview** dashboard renders summary metrics (Total Devices: 39, Online: 36, Offline: 2, Error: 1) and active alert feeds.
  3. Click **Devices Explorer** tab to inspect the device grid. Search for `AHU-` or filter by registry `ZZ-TRI-FECTA`.
  4. Click **Inspect** on **`AHU-22`** to navigate to the **Device Properties** view.
  5. Inspect the **🔄 Message Lifecycle (Model → Discovery → Proposal)** timeline cards.

---

### **Scenario 2: Automated End-to-End Playwright Testing**

* **Purpose**: Automated regression testing in headless Chromium to catch JavaScript runtime errors, broken DOM bindings, or tab routing regressions.
* **Execution**:
  ```bash
  bin/test_gummi
  # or run Playwright suite directly:
  PYTHONPATH=common/src/main/python:gencode/python:gummi:. venv/bin/pytest -v gummi/tests/test_ui_playwright.py
  ```
* **Coverage**:
  * Captures browser `pageerror` events and fails immediately on any unhandled exception or syntax error.
  * Asserts brand headers, tab switching between all 6 navigation tabs.
  * Interacts with search filters, pagination buttons, and device inspect actions.

---

### **Scenario 3: Discovery & Model Proposal Reconciliation**

* **Purpose**: Tests the end-to-end device onboarding flow from fieldbus discovery to candidate proposal generation with optimistic locking (`updateFrom`).
* **Execution**:
  ```bash
  PYTHONPATH=common/src/main/python:gencode/python:gummi:. venv/bin/pytest -v gummi/tests/test_mapping.py
  ```
* **Live UI Verification**:
  1. In the web UI on **Device Properties** for **`AHU-22`**, locate the **🔄 Message Lifecycle** panel.
  2. Click **⚡ Seed Mapping Lifecycle**.
  3. Verify the 3-step message history:
     * **Step 1: MODEL / system** — Initial site model record with `vendor.addr: "0x65"`.
     * **Step 2: EVENTS / discovery** — Scanned gateway event from `GAT-123` with `vendor.addr: "0x68"` and `bacnet.addr: "10022"`.
     * **Step 3: PROPOSE / localnet** — Reconciled proposal containing `updateFrom: "2026-08-20T10:15:30Z"` and unified addresses.

---

### **Scenario 4: Managed Fleet Rollout Campaigns**

* **Purpose**: Verifies declarative staged configuration changes across device fleets with batching and progress tracking.
* **Execution**:
  1. Navigate to the **Managed Rollout** tab in GUMMI.
  2. Click **➕ New Rollout Campaign**.
  3. Provide:
     * Campaign Name: `Firmware Update 2.4.2`
     * Device Filter: `{"make": "Acme Controls"}`
     * Subfolder: `system`
     * Payload: `{"software": {"system": "2.4.2"}}`
     * Batch Size: `5`, Interval: `30s`
  4. Submit and observe convergence progress bar and status transitions.
  5. Test **Pause** and **Cancel** buttons to halt active rollouts.

---

### **Scenario 5: Hermetic Bridgehead Integration (PostgreSQL + Mosquitto)**

* **Purpose**: End-to-end validation with live database persistence and MQTT message passing.
* **Execution**:
  ```bash
  # 1. Start local services on unprivileged port 46432
  bin/start_local sites/udmi_site_model //mqtt/localhost:46432

  # 2. Run GUMMI pointing to local services
  bin/gummi --port 8088 --mqtt-host localhost --mqtt-port 46432 --pg-host 127.0.0.1 --pg-port 5432
  ```
* **Verification**:
  * Check **Bridgehead Admin** tab in UI: PostgreSQL, InfluxDB, and Mosquitto status will report **UP** with real-time latency measurements.
  * UUFI Status in header will transition to **UUFI: Connected (Active)**.

---

## **Automated CI Integration**

The test suites for all scenarios are configured in `.github/workflows/testing.yml` under the `gummi:` job:

```yaml
gummi:
  runs-on: ubuntu-24.04
  steps:
    - uses: actions/checkout@v4
    - name: Set up Python
      uses: actions/setup-python@v5
      with:
        python-version: "3.13"
    - name: Install dependencies & Playwright
      run: |
        pip install -r requirements.txt
        pip install playwright pytest-playwright
        playwright install chromium --with-deps
    - name: Run GUMMI Test Suite
      run: bin/test_gummi
```
