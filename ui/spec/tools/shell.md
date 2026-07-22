# 🐚 Tool Specification: Host Shell

This document defines the functional requirements, layout regions, and state controls for the UDMI Workbench **Host Shell**.

---

## 1. Responsibilities

The Host Shell is the top-level orchestrator frame for the Workbench UI. It provides:
- Global brand identity (logo, title, subtitle).
- Persistent `Site Model Path` input and interactive folder browser modal.
- Primary tool navigation control (Side Navigation Rail).
- Feature flag enforcement and layout density adjustments.
- Application loading screen management.

---

## 2. Layout Regions & Component Requirements

### 2.1 Top Toolbar
- **Brand Section**: Displays logo image, "Workbench" title, and active workspace subtitle.
- **Site Model Control**:
  - Text input field displaying current `Site Model Path`.
  - Folder browse button (opens Directory Browser Modal).
  - Validation indicator: Highlights field if path is missing or invalid.

### 2.2 Navigation Sidebar
- **Tab Controls**: Vertical rail containing action buttons for active tool micro-frontends (e.g. `Sequencer`, `Mantis`).
- **Active State**: Currently selected tab is highlighted with brand primary accent.
- **Auto-Collapse Rule**: When feature policy permits only 1 active tool, the navigation sidebar automatically collapses to 0 width to give full viewport width to the single active tool.

### 2.3 Main Content Workspace
- Container area that mounts the active tool context.
- Maintains hidden/mounted states for tool views so switching tabs preserves tool UI scroll position and state without reloading.

### 2.4 Directory Browser Modal
- Floating glass panel overlay.
- Features: Current path breadcrumbs, directory listing table, parent directory button, cancel button, and path confirmation button.
