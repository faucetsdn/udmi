# 🧩 Tool Specification Template for New Micro-Frontends

This template provides the required structure for writing technology-agnostic functional specifications for any new tool micro-frontend added to the UDMI Workbench.

---

## 1. Domain Purpose & Overview
- **Tool Name**: [Insert Tool Name]
- **Primary Objective**: [Brief description of what developer problem this tool solves]
- **Target Users**: [e.g., Device integrators, QA engineers, Site managers]

---

## 2. Global State Subscriptions
List which global application states this tool needs to consume:
- [ ] `siteModel` path
- [ ] `selectedDevice`
- [ ] Backend API Authorization / Environment Metadata

---

## 3. Tool Inputs & Functional Controls
Detail top-level inputs and controls required for this tool:
- Control 1: [Purpose, input type, default value, validation rules]
- Control 2: [Purpose, input type, default value, validation rules]

---

## 4. Domain Capabilities & Functional Data Models
Describe the functional data presentation capabilities (independent of visual layout):
- **Data Model A**: [Purpose, data sources, functional capabilities]
- **Data Model B**: [Purpose, data sources, functional capabilities]

---

## 5. Backend Integration Contracts
List backend endpoints this tool will consume:
- `GET /api/...`
- `POST /api/...`
- SSE Stream: `GET /api/...`

---

## 6. Functional Error & Idle States
Define functional behavior when data is missing or loading:
- **Loading State**: [Data fetching indicator capabilities]
- **Empty State**: [User guidance when no data is returned]
- **Error State**: [Error details & retry action capabilities]
