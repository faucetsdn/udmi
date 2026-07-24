# 🎨 Visual Design Guidelines & Component Specifications

This document defines the visual hierarchy, semantic tokens, data density principles, and reusable component requirements for the UDMI Workbench user interface.

---

## 1. Design Philosophy & Aesthetic Goals

UDMI Workbench is an **engineering-grade developer tool**. Its visual design prioritizes:
- **High Data Density**: Clear, compact visual presentation maximizing readable screen space for logs, status matrices, and structured telemetry payloads.
- **Instant Visual Scanning**: Clear status indicators (Pass/Fail/Skip/Running) utilizing distinct semantic color coding.
- **High-Contrast Terminal Contrast**: Dark, readable monospace surfaces for raw log streams and payload trees to reduce visual fatigue.
- **Modern Clean Workspace Aesthetics**: Minimalist elevation, crisp borders, subtle transitions, and consistent typography aligned with Google design standards.

---

## 2. Semantic Color & Theme System

The design system relies on **semantic role assignments** rather than fixed color names. Any implementation must support light and dark theme contexts through semantic mapping:

| Semantic Role | Usage & Purpose | Example Intent |
| :--- | :--- | :--- |
| **Brand Primary** | Active tabs, primary action buttons, focused input borders, active indicators. | Standard tech blue / accent hue |
| **Surface Background** | Overall application background canvas. | Neutral light gray or deep slate |
| **Card / Panel Surface** | Grouped containers, toolbar backgrounds, modal overlays. | Elevated surface |
| **Terminal Surface** | Monospace log viewport, payload tree background, code blocks. | Deep dark gray / midnight black |
| **Status: Success / Pass**| Passed test cases, active device online status, completed runs. | Vivid emerald green |
| **Status: Failure / Error**| Failed tests, error logs, fatal runtime exceptions, stop triggers. | Crisp red |
| **Status: Warning / Alert**| Skipped tests, degraded state, pending AI analysis, cautious alerts. | Warm amber / orange |
| **Status: Info / Neutral** | Untested status, informational logs, idle indicators, neutral tags. | Cool gray / muted cyan |

---

## 3. Typography & Hierarchy

The font system is divided into two primary functional categories:

### 3.1 Proportional / Interface Font
- **Usage**: Headings, navigation tabs, button labels, control labels, table headers, modal titles.
- **Characteristics**: Clean, highly readable sans-serif (e.g., Google Sans, Roboto, Inter).
- **Scale**:
  - *Brand Title*: Bold 18px-20px
  - *Section Header*: Medium 14px-16px
  - *Control Label & Body*: Regular 12px-13px
  - *Caption & Subtitle*: Regular 11px

### 3.2 Monospace / Technical Font
- **Usage**: Terminal log output, JSON payload viewer, file paths, device IDs, raw test names.
- **Characteristics**: Crisp monospaced font with distinct character shapes (e.g., Roboto Mono, Consolas, Fira Code).
- **Scale**: 11px-12px with tight line height (1.3 - 1.4) for maximum line density.

---

## 4. Reusable Abstract Component Specs

### 4.1 Button Component
- **Variants**:
  - *Primary Button*: Solid fill with brand color. Used for main actions (e.g., "Run Sequencer", "Analyze with AI").
  - *Secondary / Outlined Button*: Subtle border, clear background. Used for secondary actions (e.g., "Browse Directory", "Clear Logs").
  - *Icon Button*: Square or circular compact button containing a single icon glyph.
- **States**: Default, Hover, Focused (visible focus outline), Active (pressed), Disabled (muted opacity, pointer events disabled), Loading (replaces label or shows spinner).

### 4.2 Status Badge / Pill Component
- **Usage**: Displaying test statuses (`PASS`, `FAIL`, `SKIP`, `RUNNING`, `UNTRIAGED`).
- **Characteristics**: Compact horizontal pill with rounded corners, bold uppercase text, and contrasting background/foreground semantic color pairing.

### 4.3 Data Table Component
- **Usage**: Displaying device matrices, test result listings, scenario listings.
- **Capabilities**:
  - Sticky header row during vertical scrolling.
  - Hover state on data rows.
  - Selected row state indicator.
  - Columns supporting text truncations with tooltips for long identifiers.

### 4.4 Terminal Log Viewer Component
- **Usage**: Real-time console log streaming and historical execution output.
- **Requirements**:
  - Dark terminal background surface.
  - Monospace text formatting.
  - ANSI color escape code parsing (converting log colors into visual text highlights).
  - Automatic line numbering.
  - **Auto-scroll Lock Feature**: Automatically scrolls to the bottom as new lines arrive; if the user manually scrolls up to inspect history, auto-scroll MUST pause until the user scrolls back to the bottom or clicks a "Scroll to Bottom" button.

### 4.5 Structured JSON Tree Inspector Component
- **Usage**: Inspecting telemetry payloads, device state objects, and AI report metadata.
- **Requirements**:
  - Hierarchical collapsible nodes (expand/collapse objects and arrays).
  - Color-coded value types (strings, numbers, booleans, nulls, keys).
  - Quick search/filter input to highlight matching keys or values.
  - "Copy to Clipboard" trigger for raw payload string.

### 4.6 Directory Browser Modal Component
- **Usage**: Interactive modal window for browsing local file paths and selecting site model directories.
- **Requirements**:
  - Breadcrumb navigation path displaying the active folder path.
  - Directory listing table showing folder names and modification indicators.
  - Quick-access location chips for **Home (`~`)**, **Root (`/`)**, and **WSL Mounts (`/mnt/c`, `/mnt/d`)** to ensure seamless navigation across WSL and non-standard filesystem setups.
  - "Parent Folder" (`..`) navigation action.
  - Confirm selection button that returns the chosen path to the calling input.

### 4.7 High-Prominence Site Model Selection Control
- **Usage**: Primary workspace selector located prominently in the top header.
- **Visual Design Requirements**:
  - **High Visual Prominence**: Must catch the eye immediately upon initial UI load. Uses high-contrast borders, bold accent styling, or a distinct elevated surface background.
  - **Status Indicator Badge**: Shows a clear visual badge (`LOADED` in success green vs. `NO SITE MODEL` in warning amber) to immediately signal to the user whether a valid workspace is connected.
  - **Initial Setup Hero Callout**: When no site model path is configured on first launch, an interactive setup banner or pulsing focus ring prompts the user to select a site model path before proceeding.

### 4.8 Structured Project Spec Builder Component
- **Usage**: Disambiguating and building target environment strings (`project_spec`) according to UDMI regex rules `[//provider/]project[/namespace][+user]`.
- **Component Layout & Controls**:
  - **Provider Selection (Dropdown)**: Allows choosing the IoT transport layer (`gbos`, `gref`, `mqtt`, `pubsub`, `clearblade`).
  - **Project Name (Text Input, Required)**: Cloud GCP Project ID (e.g. `bos-platform-dev`) or broker hostname (e.g. `localhost`).
  - **Namespace (Text Input, Optional)**: Logical prefix used for isolated topic/registry naming (e.g., `faucetsdn` or `heykhyati`). Automatically formatted with leading `/` (e.g., `/faucetsdn`).
  - **User Segment (Text Input, Optional)**: User isolation suffix (e.g., `+heykhyati`). **Validation Rule**: Automatically disabled with an inline explanatory tooltip when `gbos` or `mqtt` is selected, as user suffixes are only supported by `gref` and `pubsub`.
  - **Live Preview Bar**: Dynamically generates and displays the formatted syntax string (e.g., `//gref/bos-platform-dev/faucetsdn+heykhyati` vs. `//gbos/bos-platform-dev/faucetsdn`), helping users clearly distinguish between delimiter styles (`/namespace` vs. `+user`).
