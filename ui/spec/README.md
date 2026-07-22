# 📐 UDMI Workbench UI Specification

Welcome to the canonical specification for the **UDMI Workbench UI**.

This directory contains the technology-agnostic, complete source of truth for the Workbench user interface architecture, design principles, data contracts, state flow, and tool requirements.

---

## 🎯 Purpose of this Specification

This specification suite is designed to be **completely technology-agnostic**. It defines **WHAT** the UDMI Workbench UI does, **HOW** it behaves, and **WHAT** data contracts it relies upon—without prescribing specific web frameworks, UI libraries, or stylesheet implementations.

These specifications serve as the single source of truth to:
1. Rebuild the entire Workbench UI from scratch in any framework (e.g., React, Vue, Next.js, Svelte, Web Components, Flutter, Desktop Shell).
2. Guide automated code generators and AI coding agents to implement new tools or redesign components without breaking existing contracts.
3. Establish clear boundaries between backend API services and frontend user interfaces.

---

## 📂 Specification Index

| Document | Description |
| :--- | :--- |
| **[01-architecture-and-layout.md](./01-architecture-and-layout.md)** | Information architecture, shell-and-plugin hierarchy, micro-frontend sandboxing goals, feature flagging, and responsive viewports. |
| **[02-design-guidelines.md](./02-design-guidelines.md)** | Visual hierarchy, semantic design tokens, data density rules, dark terminal contrast principles, and reusable component specs. |
| **[03-data-and-api-contracts.md](./03-data-and-api-contracts.md)** | REST API endpoints, query parameter schemas, Server-Sent Event (SSE) log stream formats, and backend subprocess integration. |
| **[04-state-and-events.md](./04-state-and-events.md)** | Global application state, cross-module event messaging schemas, late-bound synchronization, and local storage persistence. |
| **[user-flows.md](./user-flows.md)** | End-to-end user journeys, site model selection workflows, execution loops, AI triage investigation, and error scenarios. |
| **[tools/shell.md](./tools/shell.md)** | Requirements for the outer host Shell (Header, Navigation Rail, Site Model Browser Modal). |
| **[tools/sequencer.md](./tools/sequencer.md)** | Functional requirements for the Sequencer test execution & live streaming tool. |
| **[tools/mantis.md](./tools/mantis.md)** | Functional requirements for the Mantis AI Triage & trace analysis debugger tool. |

---

## 🛠️ Ground-Rule Principles for Implementers

1. **Strict Sandboxing**: Each tool micro-frontend MUST operate in isolation. A failure or script exception in one tool view MUST NEVER crash the parent orchestrator or neighbor tools.
2. **Late-Bound State Synchronization**: When switching active tool views or loading a new tool, the global state (such as the active `siteModel` path) MUST be pushed immediately upon view readiness.
3. **No Direct Backend Subprocess Invocation**: The UI frontend never executes local processes directly; all execution and log streaming MUST route through the backend API contracts defined in `03-data-and-api-contracts.md`.
