# 📐 UI Layout & Viewport Specification

This document details the spatial layout, viewport model, navigation sidebar behavior, workspace canvas organization, panel scrolling rules, and visual responsiveness for the UDMI Workbench user interface.

> **Note**: Unlike system architectural invariants ([01-architecture.md](./01-architecture.md)), layout and viewport specifications define the changeable visual structure and can be redesigned or rebuilt dynamically.

---

## 1. Top-Level Spatial Layout Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. Persistent Top Header Bar (Brand, High-Prominence Workspace Selector)    │
├────────────┬────────────────────────────────────────────────────────────────┤
│ 2. Nav     │ 3. Main Workspace Canvas                                       │
│    Sidebar │ ┌────────────────────────────────────────────────────────────┐ │
│    (Rail)  │ │ Active Tool View (Viewport Mounted Canvas)                 │ │
│            │ │ ┌──────────────────────┬─────────────────────────────────┐ │ │
│ [Sequencer]│ │ │ Left Panel (Filters /│ Right Panel (Live Console Log   │ │ │
│ [Mantis]   │ │ │ Device Matrix)       │ Stream / Triage Inspector)      │ │ │
│            │ │ └──────────────────────┴─────────────────────────────────┘ │ │
│            │ └────────────────────────────────────────────────────────────┘ │
└────────────┴────────────────────────────────────────────────────────────────┘
```

The top-level shell layout consists of three primary spatial regions:
1. **Persistent Top Header Bar**: Fixed-height top bar housing global brand identity, workspace status badge, and the eye-catching Site Model Selection control.
2. **Navigation Sidebar (Rail)**: Vertical leading edge (left side) rail hosting action tabs for active micro-frontend tools.
3. **Main Workspace Canvas**: Flexible content container mounting the active tool viewport.

---

## 2. Viewport & Scroll Model

The UI follows a strict **Viewport-Locked Scroll Model**:

- **Root Window Viewport Lock**: The root window viewport is locked (`overflow: hidden`). The top-level document body MUST NEVER show a main browser scrollbar.
- **Fixed Shell Regions**: The Top Header Bar and Navigation Sidebar remain strictly fixed in place during all user interactions.
- **Independent Panel Scrolling**: Scrolling is confined entirely to local panel scroll containers (e.g. device data tables, monospace log viewers, JSON payload trees). Panels scroll independently without causing surrounding panels or outer headers to shift.

---

## 3. Navigation Sidebar & Single-Feature Auto-Collapse

- **Active Tab Highlight**: The currently selected tool tab is highlighted with primary visual accent styling.
- **Single-Feature Layout Auto-Collapse**: If security policies or URL parameters permit only **one** active feature (e.g. `?features=sequencer`), the Navigation Sidebar MUST automatically collapse to 0 width. This frees up maximum screen width for the active tool.

---

## 4. Micro-Frontend Tool Layout Standards

Each tool view mounted inside the workspace canvas follows standard spatial panel patterns:

### 4.1 Local Tool Toolbar
- Positioned at the top of the tool viewport.
- Houses local tool controls (Device Selectors, Filter Dropdowns, Project Spec Builder controls, Execution Buttons).

### 4.2 Split-Pane Workspaces
- **Dual-Pane Layout**: Used for tools like Sequencer (Left Pane: Device & Test Result Matrix; Right Pane: Live Console Terminal).
- **Triple-Pane Layout**: Used for complex analytical tools like Mantis (Left Pane: Chronological Event Timeline; Middle Pane: Collapsible Payload Inspector; Right Pane: AI Triage Report Viewer).
- **Independent Pane Resizing & Scrolling**: Split panes MUST support independent vertical and horizontal scrolling.

---

## 5. Responsive Design & Screen Breakpoints

- **Engineering Density (1440px+)**: Multi-pane split layouts render side-by-side to maximize data density.
- **Compact Viewports (1024px and below)**: Multi-pane tools stacked vertically or converted into collapsible accordion tabs to maintain readability without truncating data strings.
