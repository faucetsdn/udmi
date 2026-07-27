# 🏗️ Technical Architecture: AI Assistant Plugin (`ai-assistant`)

This document specifies the technical architecture, state machine, backend triage integrations, event trace engine, and query APIs for the **AI Assistant Integration** plugin tool (Mantis AI Triage & Workbench Debugger).

---

## 1. Domain Purpose & Integration Scope

The **AI Assistant** plugin acts as an intelligent co-pilot for UDMI developers and operators. It provides automated AI root cause analysis for failed Sequencer tests, answers natural language queries about device telemetry or configuration, and aids in diagnosing arbitrary system failures across the workbench.

---

## 2. Responsive Viewport State Machine (Sidebar vs Fullscreen)

The AI Assistant operates in a dynamic two-state view container model:

```
[ SIDEBAR DOCKED MODE ]  ◄── (Toggle / Expand Action) ──►  [ FULL-SCREEN WORKSPACE MODE ]
Width: 380px - 480px                                       Width: 100% Viewport Canvas
Docked on right workspace edge                             Full-screen overlay modal
```

1. **Sidebar Mode (`SIDEBAR`)**:
   - Docked on the right edge of the workspace canvas (width: 380px to 480px).
   - Allows users to monitor live test runs or inspect device matrices in the main tool while interacting with the AI Assistant.
2. **Full-Screen Mode (`FULLSCREEN`)**:
   - Expands to occupy 100% of the workspace viewport canvas.
   - Activates multi-column analytical views (Chronological Event Timeline + Telemetry Payload JSON Tree + Rendered AI Markdown Report).
3. **State Transition Sync**:
   - Clicking "Expand to Fullscreen" toggles internal state `viewMode: 'FULLSCREEN'`.
   - Dispatching `Escape` key or clicking "Collapse to Sidebar" reverts state to `viewMode: 'SIDEBAR'`, preserving active chat session history and open payload inspection nodes.

---

## 3. Backend Service Integrations & API Contracts

### 3.1 AI Triage Execution APIs
- **Launch Triage**: `POST /api/run_triage`
  - Payload: `{"site_model": "...", "device_id": "AHU-1", "test_id": "system.config", "project_spec": "..."}`
- **Stop Triage**: `POST /api/stop_triage`
- **Live Stream**: `GET /api/triage_stream` (SSE stream delivering real-time agent reasoning steps).
- **Fetch Report**: `GET /api/triage_report?site_model=...&device_id=...&test_id=...` (fetches generated Markdown report).

### 3.2 Workbench Query API (`POST /api/ai_query`)
- **Description**: Evaluates natural language user queries, telemetry diagnostics, or non-sequencer failure queries.
- **Payload**:
  ```json
  {
    "query": "Why is VAV-201 failing telemetry validation?",
    "context": {
      "site_model": "sites/udmi_site_model",
      "active_device": "VAV-201",
      "testbed_status": "HEALTHY"
    }
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "query_id": "q-9812",
    "answer_markdown": "### Analysis Summary\nDevice `VAV-201` is missing required point `supply_air_temperature_sensor` in its pointset telemetry payload."
  }
  ```

---

## 4. Telemetry Payload & Event Trace Engine

1. **Chronological Trace Collector**: Collects timestamped telemetry, state, and config payloads preceding a failure.
2. **Interactive JSON Tree Engine**: Parses raw payload JSON strings into hierarchical collapsible node trees, providing string search, node expansion, and raw copy triggers.
3. **State Preservation**: Persists chat query history, active scenario filters, and expanded report sections in memory during sidebar/fullscreen view toggles.
