# 🎨 Material 3 Layout & Design Specification: Host Shell (`shell`)

This document defines the spatial layout regions, Material 3 design system tokens, visual hierarchy, accessibility standards, and component specifications for the UDMI Workbench **Host Shell**.

---

## 1. Material 3 Top-Level Spatial Layout Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. M3 Top App Bar (Brand Identity, High-Prominence Workspace Selector)       │
├────────────┬────────────────────────────────────────────────────────────────┤
│ 2. M3 Nav  │ 3. Main Workspace Viewport Canvas                              │
│    Rail    │ ┌────────────────────────────────────────────────────────────┐ │
│            │ │ Active Plugin Tool Viewport (M3 Surface Container Low)     │ │
│ [Testbed]  │ │ (Testbed Validation / Sequencer / Mantis AI Assistant)    │ │
│ [Sequencer]│ │                                                            │ │
│ [Mantis]   │ │                                                            │ │
│            │ └────────────────────────────────────────────────────────────┘ │
└────────────┴────────────────────────────────────────────────────────────────┘
```

The shell interface is organized into three primary spatial regions following Material 3 guidelines:
1. **M3 Top App Bar**: Fixed-height top app bar (64px) using `md.sys.color.surface-container` background housing brand identity, workspace connection status badge, and the eye-catching Site Model Selection control.
2. **M3 Navigation Rail**: Vertical leading edge rail (width 80px expanded / 0px collapsed in single-feature mode) hosting M3 Navigation Destinations with active indicator pills.
3. **Main Workspace Viewport Canvas**: Flexible content container (`md.sys.color.surface`) hosting active plugin tool viewports.

---

## 2. High-Prominence Site Model Selection Control

Located centrally in the M3 Top App Bar, this component serves as the primary workspace context anchor:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 📂 Site Model: [ ~/Projects/udmi/sites/udmi_site_model         ] [Browse]   │
│                └─────── M3 Outlined Container & Focus Ring ────┘ [LOADED]  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.1 Visual Prominence & Status Badging
- **Eye-Catching M3 Elevation & Border**: Features an elevated surface container with an explicit M3 outline stroke (`md.sys.color.outline`, 2px primary focus ring).
- **Connection Status Badge**:
  - `LOADED`: Rendered as an M3 Assist Chip using `md.sys.color.primary-container` fill with `md.sys.color.on-primary-container` text when a valid site model directory path is active.
  - `NO SITE MODEL`: Rendered as an M3 Error Badge using `md.sys.color.error-container` fill when unconfigured or invalid.
- **Initial Setup Callout**: On first launch without a site model path, an M3 Banner callout with a pulsing focus highlight invites the user to select a site model path before running tools.

---

## 3. Material 3 Semantic Color & Dynamic Token System

The design system adopts **Material 3 Dynamic Color Roles** and Tonal Palettes:

| Token Name | Light Theme Value | Dark Theme Value | M3 Role & Intent |
| :--- | :--- | :--- | :--- |
| `md.sys.color.primary` | `#0061a4` | `#9ecafe` | Active tab indicators, primary action buttons |
| `md.sys.color.on-primary` | `#ffffff` | `#003258` | Text on primary fill buttons |
| `md.sys.color.primary-container` | `#d1e4ff` | `#00497d` | Active tab pill fill, selected chip surface |
| `md.sys.color.on-primary-container` | `#001d36` | `#d1e4ff` | Text on primary container fills |
| `md.sys.color.surface` | `#fdfcff` | `#1a1c1e` | Main application background canvas |
| `md.sys.color.surface-container` | `#f3f3f6` | `#202225` | Top App Bar and Sidebar background surface |
| `md.sys.color.surface-container-high` | `#e7e8ec` | `#2b2d30` | M3 Card surfaces and elevated containers |
| `md.sys.color.outline` | `#74777f` | `#8e9099` | Component borders and input outlines |
| `md.sys.color.error` | `#ba1a1a` | `#ffb4ab` | Error alerts, stop actions, failed badges |

---

## 4. Material 3 Typography Scale

- **Proportional UI Font**: Google Sans / Roboto for interface elements.
  - *Brand Title*: Title Medium (16px / 24px line-height, medium weight)
  - *Navigation Label*: Label Medium (12px / 16px line-height, medium weight)
  - *Control Label & Body*: Body Medium (14px / 20px line-height, regular weight)
- **Monospace Technical Font**: Roboto Mono / Fira Code for paths, raw log streams, and telemetry objects (12px tight density).

---

## 5. Accessibility & Viewport Locking (WCAG 2.1 AA)

1. **M3 Focus States**: Interactive elements exhibit a 2px M3 focus ring (`md.sys.color.primary`) with 2px offset.
2. **Screen Reader Landmark Roles**: Top App Bar uses `role="banner"`, Navigation Rail uses `role="navigation"`, Main Workspace uses `role="main"`.
3. **Viewport Lock Model**: Root `<body>` is locked with `overflow: hidden` to prevent outer browser scrolling; scrolling is confined to internal panel containers.
