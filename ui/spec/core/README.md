# 🏛️ Core UI Specifications (`ui/spec/core/`)

> [!NOTE]
> **PERMANENT CORE SPECIFICATIONS**  
> The specifications in this directory represent the **PERMANENT SOURCE OF TRUTH** for the UDMI Workbench UI domain capabilities, system invariants, backend API contracts, and user interaction workflows.  
> 
> **These documents remain CONSTANT across any UI rebuild or framework migration.**

---

## 📂 Core Specification Index

| Document | Description |
| :--- | :--- |
| **[01-architecture.md](./01-architecture.md)** | Constant system invariants, micro-frontend plugin model, frame sandboxing, security policy guards, and WSL path resolution. |
| **[02-functional-requirements.md](./02-functional-requirements.md)** | **WHAT to display & control**: Constant domain capabilities, workspace context, Project Spec Builder, device matrix, and AI triage views. |
| **[03-data-and-api-contracts.md](./03-data-and-api-contracts.md)** | REST API endpoints, query parameter schemas, Server-Sent Event (SSE) log stream formats, and backend subprocess integration. |
| **[04-state-and-events.md](./04-state-and-events.md)** | Global application state, cross-module event messaging schemas, late-bound synchronization, and local storage persistence. |
| **[user-flows.md](./user-flows.md)** | End-to-end user journeys, site model selection workflows, execution loops, AI triage investigation, and error scenarios. |
| **[tools/shell.md](./tools/shell.md)** | Functional requirements for the outer host Shell (Header, Navigation Rail, Site Model Browser Modal). |
| **[tools/sequencer.md](./tools/sequencer.md)** | Functional requirements for the Sequencer test execution & live streaming tool. |
| **[tools/mantis.md](./tools/mantis.md)** | Functional requirements for the Mantis AI Triage & trace analysis debugger tool. |
