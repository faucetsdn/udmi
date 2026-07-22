# 🧩 Tool Specification Template for New Micro-Frontends

This template provides the required structure for writing functional specifications for any new tool micro-frontend added to the UDMI Workbench.

---

## 1. Tool Overview & Purpose
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

## 3. Local Controls & Tool Toolbar
Detail top-level local controls required for this tool:
- Control 1: [Type, default value, interaction behavior]
- Control 2: [Type, default value, interaction behavior]

---

## 4. Main Views & Layout Panels
Describe the visual layout and data presentation regions:
- **Panel A**: [Purpose, data sources, visual presentation]
- **Panel B**: [Purpose, data sources, visual presentation]

---

## 5. Backend Integration Contracts
List backend endpoints this tool will consume:
- `GET /api/...`
- `POST /api/...`
- SSE Stream: `GET /api/...`

---

## 6. Error & Idle States
Define visual behavior when data is missing or loading:
- **Loading State**: [Spinner / Skeleton screen description]
- **Empty State**: [User guidance when no data is returned]
- **Error State**: [Alert banner & retry button]
