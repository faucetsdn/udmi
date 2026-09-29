# UDMI Workbench UI Development Guidelines

This document defines the architectural rules, UX engineering standards, and workflow principles for building the **UDMI Workbench UI**.

---

## 1. Core Principles & Philosophy

1. **Strict Contract Compliance**:
   - All frontend and integration code must strictly implement the 5 canonical contracts defined in [`workbench/WORKBENCH_CONTRACTS.md`](./WORKBENCH_CONTRACTS.md).
   - No legacy regressions: **Zero `<iframe>` elements, zero `postMessage` synchronization, and no monolithic process supervisors**.
2. **MCP-First Determinism**:
   - All deterministic backend operations (environment control, test execution, schema reading, device queries) must go through standard Model Context Protocol (MCP JSON-RPC 2.0) via `mantis/mcp_server.py` (`MCPServer`, served by the gateway at `POST /rpc` and `POST /message`).

---

## 2. UX Framework Standards

All UI development in `workbench/` must follow the atomic component hierarchy:

### 2.1. Atomic Hierarchy
* **Design Tokens (`theme.css` / tokens)**:
  - Material 3 elevation surfaces (`--bg-surface-container-low`, `--bg-surface-high`).
  - Strict semantic colors: Pass (Green `#10b981`), Fail (Red `#ef4444`), Warning/Contributing (Amber `#f59e0b`), Unresolved (Blue `#3b82f6`), Neutral (Gray `#6b7280`).
  - Standard typography tokens (Inter / Roboto for UI, Roboto Mono / JetBrains Mono for code, logs, and trace diffs).
* **Atoms**:
  - Buttons (`M3Button`: Filled, Tonal, Outlined, Icon-only).
  - Badges & Chips (`StatusBadge`: `PRIMARY`, `CONTRIBUTING`, `REFUTED`, `UNRESOLVED`, `PASSED`, `FAILED`).
  - Component Health Badges: `UP` (green), `INITIALIZING` (amber spinner), `DOWN` (grey), `ERROR` (red, overall status only). A physical DUT shows `NOT MONITORED`.
  - Form Inputs (`M3Input`, `M3Select`, `M3Toggle`).
* **Molecules**:
  - `ToolCallBadge`: Collapsible tool execution chip displaying tool name, input params, duration, and output preview.
  - `ThoughtAccordion`: Collapsible reasoning disclosure displaying internal model thinking and phase transitions (`Actor -> Critic -> Arbitrator`).
  - `MantisTriageTrigger`: Direct failure triage action button on failed test rows and in the artifact viewer.
  - `SequencerFilterBar`: Consolidated filter bar containing Minimum Stage selector, bulk selection (`All`/`None`), and status filters (`Passed`/`Skipped`/`Failed`).
  - `MetricCard`: KPI summaries (compliance score %, total devices, pass rate, flakiness index).
  - `SearchBar`: Log & device filtering with keyboard shortcuts (`/`).
* **Organisms**:
  - `LocalSetupDrawer`: Persistent collapsible slide-out drawer on `/sequencer` managing local testbed lifecycle (`bin/udmi start/stop/restart`) and Pubber emulator instances.
  - `TopologyCanvas`: Interactive SVG/node canvas rendering local pipeline topology (`DUT/Pubber -> Mosquitto Broker -> Local UDMIS Pod -> etcd State Store`) with live health indicators.
  - `DiagnosticDrawer`: Multi-turn conversational streaming feed with live token rendering, thought disclosure, and tool badges.
  - `HypothesisMatrix`: Interactive table comparing competing failure hypotheses with evidence-tier tags (`LOCAL_FILE`, `CLOUD`, `NONE`).
  - `ComplianceGrid`: Device x Test compliance matrix with sorting, filtering, and instant triage triggering.
  - `SiteRootsModal`: User authorization and allow-listing modal for custom site model directories outside the UDMI root.
  - `ArtifactModal`: Interactive modal inspecting `RESULT.log`, `sequence.md`, `sequence.png`, and trace JSONs with direct Mantis triage trigger.
  - `LogTerminal`: High-performance, virtualized terminal viewer streaming live tmux pane and service log buffers.
* **Templates & Pages** (routes in `static/js/app.js` `ROUTES`):
  - `/sequencer`: Core sequence runner and compliance testing workspace with integrated right-hand `LocalSetupDrawer`, `SequencerFilterBar`, and live console output.
  - `/devices`: Per-device compliance matrix (feature bucket x stage), run targets, report downloads, and the site-level commit dialog.
  - `/logs`: Not a view. Raises the Workbench Logs drawer (structured browser + server log ring buffers) over whichever view was last mounted.
  - Mantis is not a route: it is an omnipresent right-edge drawer (`mantis-drawer.js` hosting `mantis-panel.js`) that survives navigation between views.
  - Settings is a header dialog (`settings-dialog.js`) for email-notification consent.

### 2.2. Component Cataloging Rule
* Every new atom, molecule, or organism must have:
  1. A declarative property schema (e.g. TypeScript interface or JSON/YAML config).
  2. Clear state variations: `default`, `hover`, `active`, `focused`, `disabled`, `loading`, `error`.
  3. No tight coupling to UDMI business logic; pass data via props/slots so the component is pure and reusable.
* When a component reaches production quality, document its schema and add it to the Workbench component catalog.

---

## 3. Route-Driven Architecture & State Rules

1. **First-Class URLs (No Monolithic Single-Screen Bloat)**:
   - Every primary view must have a direct, bookmarkable URL (`/sequencer`, `/devices`; `/logs` deep-links the log drawer). The gateway serves `index.html` for these paths (`SPA_ROUTES` in `server/gateway.py`).
   - Full support for multi-tab workflows: an operator must be able to open `/sequencer` on Monitor 1 and `/devices` on Monitor 2 simultaneously.
2. **Client-Side Routing & View-Pane Caching (No DOM Destruction)**:
   - View transitions must occur client-side without full-page document reloads.
   - Views are mounted inside dedicated `.view-pane` containers; navigating between views toggles pane visibility rather than tearing down DOM nodes.
   - Long-running SSE streams (Mantis reasoning loops, live sequencer test logs), test execution state, and console logs remain alive and intact in memory when navigating between routes.
3. **Local Test Setup Drawer (Sequencer Tab Integration)**:
   - The local testbed substrate is integrated directly into `/sequencer` as a right-side collapsible drawer.
   - Toggled via the **Start Local Setup** button in the Sequences panel header or `Ctrl/Cmd+Shift+L`.
   - Allows operators to start/stop local unprivileged services (`//mqtt/localhost:<port>`, entered in the drawer), launch simulated Pubber devices, and monitor component health without leaving the test runner.
4. **Global Assistant Drawer & Deep Triage Routing**:
   - Mantis is summonable from any screen via `Cmd+K` / `Ctrl+K` or the **MANTIS** pull tab.
   - Direct failure triage triggers (**Diagnose with Mantis**, **Explain this test**) open the drawer with the site model, device, and test pre-bound; the view underneath is not replaced.

---

## 4. Backend Integration Invariants (The Contracts)

* **Contract 1 (MCP JSON-RPC & Deterministic Tool Operations)**:
  - Deterministic tool calls go through standard JSON-RPC 2.0 via `POST /message` or `POST /rpc`.
* **Local Testbed & Infrastructure Endpoints**:
  - Manage local stack lifecycle via canonical endpoints:
    - Every call below takes `project_spec` (`//mqtt/localhost:<port>`, explicit unprivileged port) from the drawer's own spec input; ports are derived from it per call, and a missing/non-local spec is a 400.
    - `POST /api/testbed/start`: Starts local infrastructure via `bin/udmi start <site_model> <spec>`.
    - `POST /api/testbed/stop`: Terminates local services via `bin/udmi stop <spec>` and verifies the spec's ports closed.
    - `POST /api/testbed/restart`: Clean-slate restart (stop, `clean <spec>`, start).
    - `GET /api/testbed/status?project_spec=...`: Returns health status and ports for `mqtt_broker`, `udmis`, `etcd`, `pubber`.
    - `GET /api/testbed/connection?project_spec=...&site_model=...&device_id=...`: Physical-device connection facts derived from broker config and the site model (`unknown` where undeterminable).
    - `POST /api/testbed/pubber/start` & `stop`: Runs or terminates the tracked background device simulator (Pubber is opt-in; Physical is the default mode).
    - `GET /api/testbed/logs`: Streams or queries recent component logs.
* **Custom Site Model Roots Endpoints**:
  - Secure directory allow-listing with user consent: `GET /api/site-roots`, `POST /api/site-roots` (`{"path": ...}`), `DELETE /api/site-roots?path=...`. Roots persist under `site_roots` in `~/.config/udmi/workbench.json`.
  - Supports dual layout conventions: `<site_dir>/cloud_iot_config.json` and `<site_dir>/udmi/cloud_iot_config.json` (canonical display: parent folder name).
* **Contract 2 (Mantis Streaming Agent)**:
  - Connect to `POST /api/mantis/chat` via Server-Sent Events (`text/event-stream`).
  - Handle all canonical event types: `phase`, `thought`, `token`, `tool_call`, `tool_result`, `hypothesis_matrix`, `diagram`, `done`, `error`.
  - Support slash commands: `/status`, `/logs <window>`, `/clear`, `/help` (`/export` exists only in the `bin/mantis` REPL).
  - Stop a run with `POST /api/mantis/chat/stop`; reset with `POST /api/mantis/chat/clear`.
  - `"notify": true` in the chat payload detaches the run from the stream and emails the result (see Email Notifications below).
* **Email Notifications**:
  - `GET /api/notifications`, `POST /api/notifications/consent` (`{"consent": bool}`), `POST /api/notifications/test`. Consent lives under `notifications.email` in `~/.config/udmi/workbench.json`, bound to the ADC account's own address (gmail.send scope). Details: `WORKBENCH_CONTRACTS.md` §5.4.1.
* **Contract 3 (Real-Time Logs)**:
  - Start a run with `POST /api/sequencer/run` (optional `"notify": true`), then stream it with `GET /api/sequencer/stream?session_id=...&offset=...` (`log`, `test_event`, `heartbeat`, `complete`, `error` events).
  - Streams are resumable by byte `offset`; on reload the view reattaches to the running session from `GET /api/sequencer/sessions`.
* **Contract 4 (Workspace State)**:
  - Maintain a centralized reactive state store conforming to `WorkspaceState` in `WORKBENCH_CONTRACTS.md`.
* **Contract 5 (Compliance & Reporting)**:
  - Use the standardized compliance matrix schema for scorecards and certification export bundles.

---

## 5. Professional UI/UX Standards

* **Accessibility (WCAG 2.1 AA)**:
  - Full keyboard navigability: `Tab` ordering, `Esc` to dismiss modals/drawers, `Cmd+K` for assistant.
  - Proper ARIA attributes: `aria-live="polite"` on streaming token containers, `aria-expanded` on accordions.
  - Minimum contrast ratio of 4.5:1 for normal text across light and dark themes.
* **Zero Layout Shifts & Viewport Locking**:
  - Fixed-viewport application layout (`100vh` / `100dvh`) with internal independent scrolling containers.
  - No double scrollbars or unwanted document-level scrolling.
* **Error Resilience & Fail Fast**:
  - Always surface actionable, technical error messages when MCP tools or API calls fail; do not swallow errors into empty states.
  - Visual loading skeletons and progress spinners during async operations.

---

## 6. Clean Architecture & Anti-Spaghetti Invariants

To prevent tight coupling, tangled state mutations, and unmaintainable "spaghetti code," the Workbench codebase enforces a **strict 4-layer unidirectional architecture**:

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Layer 1: Pure Presentation (static/js/components/)                         │
│ - Stateless Atoms, Molecules, Organisms                                    │
│ - Driven 100% by typed props & upward semantic event callbacks             │
│ - FORBIDDEN: fetch(), EventSource, direct store mutation, route awareness  │
└────────────────────────────────────▲───────────────────────────────────────┘
                                     │ Props down / Events up
┌────────────────────────────────────┴───────────────────────────────────────┐
│ Layer 2: Route Views & Reactive State Store (js/views/, js/core/store.js)  │
│ - Route-level controllers (/sequencer, /devices) and run-session.js       │
│ - Central WorkspaceState store (Contract 4)                                │
│ - Orchestrates Layer 3 service clients and passes state to Layer 1         │
│ - FORBIDDEN: Raw HTTP/SSE protocol parsing, inline JSON-RPC formatting     │
└────────────────────────────────────┬───────────────────────────────────────┘
                                     │ Typed Interface Calls
┌────────────────────────────────────▼───────────────────────────────────────┐
│ Layer 3: Contract Service Adapters (static/js/core/api.js, logger.js)      │
│ - IMcpClient (Contract 1), IMantisStreamClient (Contract 2),               │
│   ILogStreamClient (Contract 3), IComplianceClient (Contract 5)            │
│ - Encapsulates JSON-RPC 2.0 envelopes, SSE parsing, retries & timeouts     │
│ - Validates wire payloads at the boundary before returning typed models    │
└────────────────────────────────────┬───────────────────────────────────────┘
                                     │ HTTP / SSE + X-Correlation-ID
┌────────────────────────────────────▼───────────────────────────────────────┐
│ Layer 4: Backend Gateway & Domain Engines (workbench/server/, mantis/)     │
│ - Thin HTTP/SSE adapters delegating to mantis/mcp_server.py & mantis/      │
│ - Zero UI presentation logic; strict Pydantic/JSON-Schema validation       │
└────────────────────────────────────────────────────────────────────────────┘
```

### 6.1. Structural & Dependency Rules
1. **Strict Downward Dependency Direction**:
   - `Layer 1 (Components)` may ONLY import design tokens and shared TypeScript data interfaces (`types/`).
   - `Layer 2 (Views/Store)` may import `Layer 1` components, `Layer 3` service interfaces, and `types/`. Views must NEVER import other views.
   - `Layer 3 (Services)` may ONLY import `types/` and the structured logger (`utils/logger`). Services must NEVER import UI components or view modules.
2. **Explicit Interface Contracts (Zero Leaky Abstractions)**:
   - Every external communication boundary must be backed by an explicit interface (`IMcpClient`, `IMantisStreamClient`, `ILogStreamClient`, `IComplianceService`) and dedicated domain types (`types/contracts.ts` mirroring `WORKBENCH_CONTRACTS.md`).
   - Using untyped `any` payloads, ad-hoc dictionary bags, or raw `fetch()` / `new EventSource()` inside components or views is strictly prohibited.
3. **Single-Responsibility Modules & File Size Guardrails**:
   - Each module must have a single, clear responsibility (one component per file, one contract client per service file, one slice/domain per store module).
   - No file may exceed **300 lines of code** without decomposing sub-components or helper modules.

---

## 7. Structured Logging & End-to-End Observability

Observability is a first-class architectural requirement across both the frontend SPA and backend gateway:

### 7.1. Unified Structured Log Schema
All logs across client and server must be emitted as structured records (never ad-hoc `console.log("here", data)` or `print()` statements):

```typescript
export interface LogEntry {
  timestamp: string;          // ISO-8601 UTC timestamp
  level: 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';
  layer: 'UI' | 'STORE' | 'MCP_RPC' | 'MANTIS_SSE' | 'LOG_STREAM' | 'GATEWAY';
  correlationId: string;      // End-to-end trace ID (e.g. 'req-8f3a2b1c')
  module: string;             // e.g. 'McpClient', 'TriageView', 'WorkspaceStore'
  event: string;              // Canonical event name, e.g. 'rpc.call.start', 'sse.event.received'
  durationMs?: number;        // Execution latency for operations/requests
  context?: {
    sessionId?: string | null;
    deviceId?: string;
    testId?: string;
    toolName?: string;
  };
  details?: Record<string, unknown>;
  error?: {
    code: string;
    message: string;
    stack?: string;
  };
}
```

### 7.2. End-to-End Correlation Propagation (`X-Correlation-ID`)
1. **Origin Generation**: Every user-initiated action, MCP tool call, or Mantis chat request generates a unique `correlationId` (or uses the JSON-RPC `id`).
2. **Wire Propagation**: Layer 3 clients attach `X-Correlation-ID: <correlationId>` to all HTTP/SSE requests (`POST /rpc`, `POST /api/mantis/chat`, `GET /api/sequencer/stream`).
3. **Backend Echo & Tagging**: The backend gateway reads `X-Correlation-ID`, binds it to the request context logger, logs tool/agent execution duration and status with that ID, and returns `X-Correlation-ID` in the response headers and error payloads.

### 7.3. Mandatory Instrumentation Points
* **Contract 1 (MCP JSON-RPC)**: Log `rpc.call.start` (method, tool name, sanitized args) and `rpc.call.complete` / `rpc.call.error` (with `durationMs` and error code).
* **Contract 2 & 3 (SSE Streams)**: Log stream lifecycle transitions (`sse.connect`, `sse.phase_change`, `sse.reconnect_scheduled`, `sse.closed`, `sse.error`) without flooding the log buffer on individual text tokens.
* **Contract 4 (Workspace State Store)**: Log state transitions (`store.mutation`) with action name, previous/next summary diff, and triggering source.
* **Client-Side Diagnostic Ring Buffer**: The frontend `WorkbenchLogger` maintains a bounded in-memory ring buffer (last 1,000 structured entries) shown, merged with the server's ring buffer (`GET /api/diagnostics/logs`), in the Workbench Logs drawer.

