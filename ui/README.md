# 🛠️ UDMI Workbench: Codebase & Architecture Guide

Welcome to the developer documentation for the **UDMI Workbench** codebase. This guide details the technical architecture, directory structure, shared design system, and extension instructions for developers looking to maintain or add new tools to the Workbench suite.

---

## Pluggable Micro-Frontend Architecture

UDMI Workbench is built on a **Micro-Frontend Architecture** using sandboxed `<iframe>` elements. Rather than compiling all tools into a single monolithic codebase, Workbench treats every tool as a **first-class, completely independent plugin**.

```
              ┌──────────────────────────────────────────┐
              │          Parent Orchestrator             │
              │         (ui/index.html & main.js)        │
              └───────┬──────────────────────────┬───────┘
                      │                          │
           postMessage│(siteModel)    postMessage│(siteModel)
                      ▼                          ▼
        ┌──────────────────────────┐┌──────────────────────────┐
        │  Sequencer Dashboard     ││     Mantis Debugger      │
        │ (ui/sequencer/index.html)││  (ui/mantis/index.html)  │
        │  - Run/Stop Compliance   ││  - Chronological Trace   │
        │  - Live Log Streamer     ││  - Payload Tree Inspector│
        └──────────────────────────┘└──────────────────────────┘
```

### Key Architectural Benefits:
*   **Absolute Sandboxing**: Each tool runs in its own isolated iframe context. A script crash, syntax error, or unhandled exception inside one tool (e.g., Mantis) can never bleed or crash another tool (e.g., Sequencer) or the main orchestrator.
*   **Perfect Style Isolation**: CSS classes inside a tool's stylesheet are isolated to its iframe. This completely prevents CSS selector bleeding or global styling contamination.
*   **Decoupled Development**: Different developers can build, update, or customize tools independently without needing to touch or coordinate changes in the core orchestrator or other plugins.

---

## 📂 Codebase Directory Layout

```
ui/
├── assets/                  # Shared image assets (logos, icons)
├── shared/                  # Shared Design System & Components
│   ├── components/          # Reusable UI components (JSON Tree, Log Terminal)
│   └── theme.css            # Material 3 design tokens & unified form styles
├── sequencer/               # Standalone Sequencer Micro-Frontend
│   ├── index.html           # Sequencer UI layout & local toolbar
│   ├── main.js              # Sequencer controller (local DUT scan & run)
│   └── style.css            # Local viewport-locked styles
├── mantis/                  # Standalone Mantis Debugger Micro-Frontend
│   ├── index.html           # Mantis UI layout & local scenario toolbar
│   ├── main.js              # Mantis controller (scenario scan & timeline)
│   └── style.css            # Local transition tree & JSON viewer styles
├── index.html               # Parent Shell HTML (Nav Rail & Iframe mounts)
├── main.js                  # Parent Shell Orchestrator (State sync, modals)
├── style.css                # Parent Shell Styles (Outer layouts, modal overlays)
└── server.py                # Python API & Static File Server (Request guards)
```

---

## State Sync Engine (PostMessage API)

The parent orchestrator (`ui/main.js`) manages the global **Site Model Path** selection. To keep the child tools updated without forcing iframe reloads, the shell broadcasts state changes downstream using the HTML5 **PostMessage API**.

### 1. Parent Broadcast
Whenever a user changes the Site Model (via the text input or the Browse modal), the parent shell broadcasts the update:
```javascript
syncStateToIframe(iframe) {
  const sitePath = this.siteInput.value.trim();
  if (iframe.contentWindow) {
    iframe.contentWindow.postMessage({
      type: 'udmi_state_change',
      siteModel: sitePath
    }, '*');
  }
}
```
*   **Late-Bound Sync**: To ensure an iframe receives the state even if it is still loading, the parent shell listens for the `load` event of each iframe and pushes the active Site Model path the exact millisecond the frame finishes loading, ensuring zero-latency initializations.

### 2. Child Subscription
Inside each micro-frontend (e.g., `ui/sequencer/main.js`), the tool listens for this message to trigger its local scans:
```javascript
window.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'udmi_state_change') {
    this.handleGlobalStateChange(event.data.siteModel);
  }
});
```

---

## How to Extend: Add a New Tool in 5 Minutes!

Adding a new developer tool (for example, a **`config_editor`**) is incredibly simple and requires **zero changes** to the business logic of existing tools.

### Step 1: Create a Plugin Directory
Create a new folder under `ui/` for your tool:
```bash
mkdir -p ui/config_editor
```

### Step 2: Create your Standalone HTML/JS/CSS Files
Create your tool layout and logic. 

**`ui/config_editor/index.html`**:
Make sure to load the shared design system styles (`../shared/theme.css`) and your local assets:
```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Config Editor</title>
  <!-- Load shared theme stylesheet first! -->
  <link rel="stylesheet" href="../shared/theme.css">
  <link rel="stylesheet" href="style.css">
</head>
<body>
  <div class="control-group">
    <label class="control-label">Target Device</label>
    <input type="text" id="local-device-input" class="form-input" disabled>
  </div>
  <script type="module" src="main.js"></script>
</body>
</html>
```

**`ui/config_editor/main.js`**:
Subscribe to the parent orchestrator's state broadcasts. The shell will automatically push the active Site Model path whenever it loads or changes:
```javascript
class ConfigEditorController {
  constructor() {
    this.siteModel = '';
    
    // 1. Subscribe to the parent postMessage state broadcasts
    window.addEventListener('message', (event) => {
      if (event.data && event.data.type === 'udmi_state_change') {
        this.handleGlobalStateChange(event.data.siteModel);
      }
    });
  }

  handleGlobalStateChange(siteModel) {
    this.siteModel = siteModel;
    console.log(`Config Editor received global site model path: ${siteModel}`);
    // Query directory contents, load config schemas, etc.
  }
}

window.addEventListener('DOMContentLoaded', () => new ConfigEditorController());
```

### Step 3: Mount your Tool in the Parent Shell
Open **`ui/index.html`**:
1.  Add your iframe inside the `<section class="app-content">` block:
    ```html
    <iframe id="iframe-config" class="app-iframe" src="config_editor/index.html" data-feature="config_editor"></iframe>
    ```
2.  Add a navigation rail tab inside the `<aside class="app-sidebar">` rail:
    ```html
    <button class="sidebar-tab" data-tab="config" data-feature="config_editor">
      <span class="material-symbols-outlined">edit_note</span>
      <span>Config Editor</span>
    </button>
    ```

### Step 4: Register the Feature Flag
Open **`ui/server.py`**:
Add your new feature name (`'config_editor'`) to the global `ALLOWED_FEATURES` registry to authorize its network path:
```python
ALLOWED_FEATURES = {'sequencer', 'mantis', 'config_editor'}
```

That's it! Your tool is now fully integrated. You can launch it using `bin/workbench config_editor` or open the unified suite and access your new tab with complete sandboxing!

---

## Shared Design System (`ui/shared/`)

To keep the visual interface premium, cohesive, and aligned with Material 3 principles, all layout stylesheets must inherit their variables and base rules from **`ui/shared/theme.css`**:

### 1. Stepped Container Depth
We enforce standard background elevation tokens, giving the workspace a highly polished, layered depth aesthetic:
*   `--bg-app` / `--bg-surface-container-lowest` (`#f8fafd`): Application canvas.
*   `--bg-surface-container-low` (`#f0f4f9`): Medium contrast containers (e.g. textboxes, dropdowns).
*   `--bg-surface-high` (`#e9eef6`): High contrast cards and highlights.
*   `--bg-surface-highest` (`#dde3ea`): Strongest contrast borders and neutral badges.
*   --bg-surface (`#ffffff`): White cards, dropdown lists, and modal bodies.

### 2. Unified Form Elements
Every text input, selector dropdown, focus glow, and disabled state across all micro-frontends is standardized in `theme.css`. 
*   **Text Inputs**: Apply class **`.form-input`**
*   **Selector Dropdowns**: Apply class **`.form-select`**
*   **Control Labels**: Wrap in a `.control-group` div and apply class **`.control-label`** on the label tag to automatically style micro-typography titles.
*   **Buttons**: Apply class **`.btn`** and primary/outlined modifier (e.g. `.btn-primary`, `.btn-outlined`).
