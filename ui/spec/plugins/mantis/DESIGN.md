# 🎨 Layout & Design Specification: Mantis AI Assistant Plugin (`mantis`)

This document defines the layout, Material 3 design tokens, responsive Side Sheet vs. Full-Screen view modes, component specifications, and accessibility standards for the **Mantis AI Assistant Plugin** tool.

---

## 1. Material 3 Top-Level Spatial Layout Models

### 1.1 M3 Standard Side Sheet Mode (Docked View)
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 🤖 Mantis AI Assistant                           [ ⛶ Expand ] [ ✖ Close ]  │
├─────────────────────────────────────────────────────────────────────────────┤
│ M3 Outlined Card: Scenario Configuration                                   │
│ Target Device: [ AHU-1 ▾ ]   Failed Test: [ system.config ▾ ]               │
│ [ M3 Filled Button: ▶ Run AI Triage ]                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│ 💬 Chat & Query History (M3 Surface Container Low)                          │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ User: Why did system.config fail?                                       │ │
│ │ AI: Missing field `system.min_power` in state message.                  │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────────┤
│ M3 Outlined Text Field: [ Type query or failure prompt... ]       [Send FAB]│
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 M3 Full-Screen Workspace Mode (Expanded Canvas)
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 🤖 Mantis AI Workspace                           [ 🗗 Collapse to Side Sheet]│
├─────────────────────────────────────┬───────────────────────────────────────┤
│ Left Column: Diagnostics & Context  │ Right Column: Analysis & Triage Report│
│ ┌─────────────────────────────────┐ │ ┌───────────────────────────────────┐ │
│ │ 🕒 Chronological Event Timeline  │ │ │ # 📋 Root Cause Analysis Report   │ │
│ │ (M3 Surface Container High)     │ │ │ (M3 Surface Container Lowest)     │ │
│ │  • 10:15:00 Telemetry Received   │ │ │                                   │ │
│ │  • 10:15:02 State Ack Sent      │ │ │ ## Failure Summary                │ │
│ │  • 10:15:05 Config Error [FAIL]  │ │ │ Device failed system.config state │ │
│ ├─────────────────────────────────┤ │ │ missing required property.        │ │
│ │ 🔍 Telemetry Payload Inspector  │ │ │                                   │ │
│ │  ▼ { system: {                  │ │ │ ## Remediation Steps              │ │
│ │      min_power: null [!]        │ │ │ 1. Update site model config.      │ │
│ │    }}                           │ │ └───────────────────────────────────┘ │
│ └─────────────────────────────────┘ │ [💬 Ask Query Bar                     ]│
└─────────────────────────────────────┴───────────────────────────────────────┘
```

---

## 2. Material 3 Design Tokens & Tonal System

| Token Name | Light Mode Value | Dark Mode Value | M3 Role & Usage |
| :--- | :--- | :--- | :--- |
| `md.sys.color.primary` | `#0061a4` | `#9ecafe` | Primary action buttons, active tab indicators |
| `md.sys.color.on-primary` | `#ffffff` | `#003258` | Text on primary buttons |
| `md.sys.color.primary-container` | `#d1e4ff` | `#00497d` | Accent containers and active pills |
| `md.sys.color.surface` | `#fdfcff` | `#1a1c1e` | Base workspace surface |
| `md.sys.color.surface-container-low` | `#f3f3f6` | `#1e2022` | M3 Card surfaces and timeline background |
| `md.sys.color.surface-container-high` | `#e7e8ec` | `#2b2d30` | Selected payload inspector surface |
| `md.sys.color.outline` | `#74777f` | `#8e9099` | M3 Outlined Card borders and input outlines |
| `md.sys.color.error` | `#ba1a1a` | `#ffb4ab` | Failed test badges and error highlights |
| `md.sys.color.error-container` | `#ffdad6` | `#93000a` | Failure callout background tint |

---

## 3. Material 3 Component Specifications

### 3.1 M3 Side Sheet Component
- **Behavior**: Standard M3 Side Sheet docked to the right edge of the workspace canvas (width 400px–480px), using `md.sys.color.surface-container-low` background and `1px solid md.sys.color.outline-variant` leading border.
- **Header Actions**: M3 Standard Icon Buttons for "Expand to Fullscreen" (`⛶`) and "Close" (`✖`).

### 3.2 M3 Full-Screen Dialog Canvas
- **Behavior**: Expands to 100% viewport width and height when full-screen mode is active.
- **Shortcut**: Pressing `Escape` or clicking "Collapse to Side Sheet" (`🗗`) transitions smoothly back to Side Sheet mode.

### 3.3 M3 Chronological Event Timeline
- Vertical timeline with M3 Tonal Badge nodes. Nodes change fill to `md.sys.color.error-container` on test failure events.

### 3.4 M3 Telemetry Payload Inspector
- Hierarchical collapsible JSON tree rendered with monospaced typography (`Roboto Mono`).
- Value types styled with M3 color roles: strings (`md.sys.color.primary`), numbers (`#00838f`), booleans (`#e65100`), missing properties (`md.sys.color.error`).
- M3 Assist Chip button: "Copy Payload JSON".

### 3.5 M3 Markdown Report Viewer
- Rendered on `md.sys.color.surface-container-lowest` card surface with M3 Type Scale (Title Large, Body Medium, Code blocks).
- **Confidence Badge**: M3 Filter Chip displaying confidence score (`95% High Confidence` in green vs `60% Low Confidence` in amber).

---

## 4. Accessibility & WCAG 2.1 AA Standards

1. **M3 Focus States**: Interactive elements exhibit a 2px M3 focus ring (`md.sys.color.primary`).
2. **ARIA Landmark Roles**: Side Sheet container uses `role="region"` with `aria-label="Mantis AI Assistant"`; Full-Screen mode uses `role="dialog"` and `aria-modal="true"`.
3. **Contrast Ratios**: All M3 color roles guarantee minimum 4.5:1 text contrast ratio against their respective surface containers.
