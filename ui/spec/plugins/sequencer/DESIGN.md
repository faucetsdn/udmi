# 🎨 Material 3 Layout & Design Specification: Sequencer Plugin (`sequencer`)

This document defines the layout, Material 3 design tokens, Project Spec Builder controls, terminal streamer, differential log viewer, and accessibility standards for the **Sequencer Test Execution** plugin tool.

---

## 1. Material 3 Top-Level Spatial Layout Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. Sequencer Execution Toolbar & M3 Project Spec Builder Controls          │
│ [M3 Dropdown: gref ▾] [M3 Outlined Field: bos-platform-dev] [/faucetsdn]   │
│ [+heykhyati] [Live Preview: //gref/bos-platform-dev/faucetsdn+heykhyati]     │
│ [Device: AHU-1 ▾]  [Min Stage: ALPHA ▾]  [ ▶ Run Sequencer ] [ ⏹ Stop ]     │
├──────────────────────────────────────┬──────────────────────────────────────┤
│ 2. Left Pane: M3 Device Matrix Card  │ 3. Right Pane: M3 Dual-Tab Log &     │
│ ┌──────────────────────────────────┐ │    Differential Analysis Workspace   │
│ │ M3 Outlined Table                │ │ [ M3 Segmented Tab: Live ] [ Diff ]  │
│ │ Device: AHU-1                    │ │ ┌──────────────────────────────────┐ │
│ ├──────────────────────────────────┤ │ │ 10:15:01 [INFO] Starting test... │ │
│ │ pointset.telemetry   [ PASS ]    │ │ │ 10:15:03 [PASS] pointset         │ │
│ │ system.config        [ FAIL ] ──►│ │ │ 10:15:05 [FAIL] system.config    │ │
│ │                      [Triage]    │ │ └──────────────────────────────────┘ │
│ │                      [Diff]      │ │                                      │
│ └──────────────────────────────────┘ │                                      │
└──────────────────────────────────────┴──────────────────────────────────────┘
```

The workspace is organized into three primary regions using Material 3 containers:
1. **M3 Execution Toolbar & Project Spec Builder**: Top bar housing M3 Dropdowns, M3 Outlined Text Fields, live syntax preview bar, device selector, stage filters, and M3 Filled Action Buttons (`▶ Run Sequencer`, `⏹ Stop`).
2. **Left Pane: M3 Device & Compliance Matrix Card**: M3 Outlined Card container displaying discovered devices, test execution stages, pass/fail M3 Badges, and quick action chips (`Triage with AI`, `Compare Diff`).
3. **Right Pane: Dual-Tab Console & Differential Log Viewer**:
   - **M3 Segmented Button Tab A (Live Terminal)**: Dark surface container with ANSI color highlights and auto-scroll lock.
   - **M3 Segmented Button Tab B (Differential Log Viewer)**: Side-by-side or unified diff comparing current failing run logs against baseline successful run logs.

---

## 2. Material 3 Differential Log Viewer Specification

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Baseline Run: seq-20260720-1045 (PASSED) vs Current Run: seq-8912 (FAILED)  │
├─────────────────────────────────────────────────────────────────────────────┤
│  Line │ Baseline Log (Pass)              │ Current Log (Fail)               │
│ ──────┼──────────────────────────────────┼──────────────────────────────────│
│   12  │ INFO Sending system config...    │ INFO Sending system config...    │
│   13  │ INFO Config ACK received from dev│ - [MISSING IN CURRENT RUN]       │
│   14  │ -                                │ + ERROR Config ACK timeout 30000ms│
└─────────────────────────────────────────────────────────────────────────────┘
```

- **Color Coding**:
  - `Missing Line`: Red tint background (`rgba(186, 26, 26, 0.15)`) with leading `-`.
  - `Added Error Line`: Green/Amber tint background (`rgba(0, 97, 164, 0.15)`) with leading `+`.
- **M3 Controls**: M3 Assist Chip baseline selector dropdown, fold unchanged lines toggle, export report button.

---

## 3. Terminal Log Viewer Specs

- **Monospace Font**: `Roboto Mono` or `Fira Code` at 12px density.
- **ANSI Color Code Support**: Converts ANSI escape sequences into semantic M3 color highlights.
- **Auto-Scroll Lock**: Automatically scrolls to bottom during execution; if user scrolls up to inspect history, auto-scroll pauses and an M3 Extended FAB ("Scroll to Bottom") appears.

---

## 4. Accessibility & WCAG 2.1 AA Standards

1. **High Contrast Diff Highlights**: Diff text maintains minimum 4.5:1 contrast against surface container backgrounds.
2. **Keyboard Execution**: `Ctrl+Enter` / `Cmd+Enter` launches Sequencer run; `Escape` stops active run.
3. **Screen Reader Live Region**: Terminal console stream uses `aria-live="polite"` so new log output is announced cleanly to screen reader users.
