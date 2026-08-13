# 🎨 Material 3 Layout & Design Specification: Testbed Validation Plugin (`testbed`)

This document defines the layout, dynamic diagram visual specifications, Material 3 design system tokens, component standards, and accessibility guidelines for the **Testbed Architecture & Setup Validation** plugin tool.

---

## 1. Material 3 Top-Level Spatial Layout Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. M3 Overall Testbed Health Banner (Status: HEALTHY / DEGRADED)            │
├──────────────────────────────────────────┬──────────────────────────────────┤
│ 2. Dynamic Setup Topology Diagram Canvas │ 3. Component Setup & Connectivity│
│    (M3 Surface Container Low)            │    Checklist Panel               │
│ ┌──────────────────────────────────────┐ │ ┌──────────────────────────────┐ │
│ │  [Site Model] ──► [Validator]        │ │ │ M3 Outlined Card             │ │
│ │                       │              │ │ │ ✔ Site Model Path: Valid     │ │
│ │                       ▼              │ │ │ ✔ MQTT Broker: Connected     │ │
│ │                  [MQTT Broker]       │ │ │ ✔ Sequencer: Ready           │ │
│ │                       │              │ │ │ ✖ UDMIS Core: Not Responding │ │
│ │                       ▼              │ │ ├──────────────────────────────┤ │
│ │                  [Sequencer]         │ │ │ [M3 Filled Button: Re-Validate│ │
│ └──────────────────────────────────────┘ │ └──────────────────────────────┘ │
└──────────────────────────────────────────┴──────────────────────────────────┘
```

The workspace is organized into three primary regions using Material 3 containers:
1. **Overall Setup Health Banner**: Top M3 Banner component displaying high-level system readiness status (`ALL SYSTEMS GO` vs `SETUP ISSUES DETECTED`).
2. **Dynamic Setup Topology Diagram Canvas**: Interactive SVG canvas using `md.sys.color.surface-container-low` rendering M3 Elevated Card nodes for setup components (DUT, MQTT Broker, Validator, Sequencer, UDMIS).
3. **Component Setup & Connectivity Checklist Panel**: M3 Outlined Card container displaying component health checks, ping latencies, and diagnostic troubleshooting steps.

---

## 2. Material 3 Topology Diagram Node Specs

- **M3 Node Cards**: Elevated M3 Cards using `md.sys.color.surface-container-high` with 8px rounded corners and subtle M3 tonal elevation.
- **Node Status Badging**:
  - `UP` / `READY`: M3 Tonal Badge with `md.sys.color.primary-container` fill and green checkmark icon.
  - `DEGRADED` / `WARNING`: M3 Amber Badge with warning triangle icon.
  - `DOWN` / `ERROR`: M3 Error Badge using `md.sys.color.error-container` fill and alert icon.
- **Dynamic Connection Edges**: Animated dashed connection strokes displaying real-time message flow between connected components.

---

## 3. Accessibility & WCAG 2.1 AA Standards

1. **Colorblind-Safe Node Statuses**: Every node combines distinct text status labels (`UP`, `DOWN`, `READY`), geometric icon shapes (check, cross, triangle), and M3 color roles.
2. **Keyboard Navigability**: Dynamic diagram nodes support standard keyboard focus sequence (`Tab`, arrow keys), displaying interactive component details on `Enter` or `Space`.
3. **Screen Reader Support**: Diagram canvas includes `aria-label="Testbed Architecture Topology Diagram"` and accessible text summary listing all connected nodes and statuses.
