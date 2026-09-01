# **GUMMI Technical Specification**

## *Author: [Trevor Pering](mailto:peringknife@google.com), Updated: Aug 20, 2026* 

GUMMI (Glorious Unified Methodical Management Interface) aims to provide a singular "management plane" interface over the UDMI system.

By defining the interface boundaries and core functional requirements, this specification enables the rapid creation of a prototype used to gather direct user feedback on infrastructure fleet management workflows. The goal is to move from abstract management concepts to a tangible, interactive tool that validates operator needs.

## **Background & Context**

GUMMI represents the 'leaves' of the broader GUM Tree project concept. It is designed as a web-based user interface (UI) front-end that sits on top of a UDMI (Universal Device Management Interface) back-end.

While UDMI handles the underlying data standards, schema validation, and communication with infrastructure devices, GUMMI provides human operators with the visual tools necessary to manage and monitor a large-scale fleet of infrastructure components efficiently and methodically. It bridges the gap between raw machine telemetry and actionable human intelligence.

## **Interface Summary**

* **Portfolio Overview**: High-level view of everything  
* **Site Properties**: Details about an individual site  
* **Devices Explorer**: Handling filtered lists of devices  
* **Device Properties**: Details about one single device  
* **Configuration Management**: Managing config changes for one devce  
* **Managed Rollout**: Staged config changes across a set of devices  
* **Bridgehead Admin**: System administration for a local setup

## **Implementation Specification**

* Implemented as a web-app using the Flask Python framework.  
* Data access and storage schemas are defined and managed by the Butler subsystem ([butler/](file:///home/peringknife/udmi/butler/)).  
* Interface with the rest of the system is through the UDMI UUFI message-passing interface ([docs/specs/uufi.md](file:///home/peringknife/udmi/docs/specs/uufi.md)).  
* Test infrastructure management and local integration for agents is handled via the UDMI Test Infrastructure MCP Server (`bin/test_infra_mcp` / `bin/test_setup`), as defined in [docs/specs/uufi.md](file:///home/peringknife/udmi/docs/specs/uufi.md#99-agentic-test-infrastructure-management-mcp-server--cli).  
* The UI is declarative: its primary write function is setting the desired state, while state reconciliation and execution are handled by external backend systems.  
* Supported authentication methods are No Authentication (relying on network-level access controls) and Identity-Aware Proxy (IAP).

# **Scope of Features**

The prototype should implement the following core interface to handle end-to-end fleet management. Each interface is manifested as a tabbed pane in the main window.

## **Portfolio Overview**

Primary focus is coverage across all the registries in the portfolio. The dashboard provides an immediate situational awareness of the entire ecosystem.

* **Summary Metrics**: High-level counters for total devices, online/offline status, and active error states.  
* **Alert Feed**: A prioritized list of recent critical events across the entire fleet requiring operator attention.  
* **Geographical/Logical Grouping**: Visual breakdown of the fleet by location or site hierarchy, allowing operators to pivot between global and regional views.

## **Devices Explorer**

Primary focus is detailing all the devices in a given registry (or a filtered set of devices across all registries). A comprehensive list for navigating thousands of individual components.

* **Tabular View**: A robust grid listing all infrastructure devices with columns for Device ID, Type, Status, Last Seen, and Site.  
* **Filtering & Search**: Ability to filter the fleet by status, device type, or specific UDMI tags, along with a global text search.  
* **Bulk Actions**: Basic selection mechanism (checkboxes) to prepare for future bulk configuration updates or mass reboot commands.

## **Device Properties**

Primary focus is all the properties of a given device. Deep-dive inspection for troubleshooting individual hardware units.

* **Telemetry Visualization**: Real-time or near-real-time display of device state variables and points as defined by UDMI schemas (e.g., temperature, pressure, power consumption).  
* **State vs. Config Comparison**: A side-by-side view showing the current reported 'State' of the device against its desired 'Config' target, highlighting discrepancies.  
* **Event History**: A chronological log of recent state transitions, errors, and configuration changes for the specific device.

## **Configuration Management**

Primary focus is defining and declaring target device configurations. GUMMI specifies the desired state for a device, while underlying infrastructure services handle state reconciliation and execution.

* **Desired State Editor**: A user-friendly form or JSON editor to set and declare a device's desired configuration based on the UDMI standard.  
* **State Comparison & Validation**: Visual comparison between the defined desired state and the currently reported state, with schema validation indicators before submitting changes.

## **Managed Rollout**

Primary focus is declaratively applying target configurations across selected sets of devices. GUMMI provides simple mechanisms to select devices and set their desired state, leaving rollout execution and reconciliation to external backend systems.

* **Device Selection:** Mechanisms to query and target specific groups of devices for configuration updates.  
* **Desired State Declaration:** Specifies the target configuration changes to be applied across the selected devices.  
* **State Comparison Overview:** Displays current vs. desired states across targeted devices to monitor convergence.

## **Bridgehead Administrative Panel**

Primary focus is on the system health of locally installed system components. The ability to check the status of local bridgehead components (e.g. mosquitto instance), and ensure that everything is operating normally for a local install. This does not necessarily scale to handle cloud-based deployments (e.g. UDMIS running across a k8s cluster).

# **Common Capabilities**

### **Device Filters**

Many views will have the ability to filter devices to select an operative subset. There should be a common mechanism, implemented in a consistent way across the various pages, for selecting which devices to view and operate on. All filtering must be implemented server-side to support large-scale fleet datasets. This ability should include, but not be limited to:

* registryId, as a set selection mechanism (often will be *none* or just one, but might be multiple but there's no meaningful patter to them)  
* deviceId, either from a set or as a prefix of the form `XXX-` where XXX is a set of ALL CAPS letters.  
* system.hardware make, model, etc…  
* system.software.component value matching

### **Result Paging**

The backend DB might have 100k+ entries in it, which would be intractable to view. There needs to be a managed way to surface the high number of potential entries into the front end.

* Standard pagination interface for displaying 100 results at a time in the UI  
* Standard server-side LIMIT/OFFSET pagination to efficiently serve results to the UI  
* Dynamic DB fetching logic to handle filter queries

# **High-Level Technical Architecture**

The system follows a decoupled front-end/back-end architecture optimized for rapid UI prototyping and iterative feedback.

## **Technical Configuration**

Each instance of GUMMI will need to have the following configuration parameters defined to be functional:

* **UUFI connection**: The connection URL and credentials required to connect to the UUFI channel (defined in [docs/specs/uufi.md](file:///home/peringknife/udmi/docs/specs/uufi.md) and dynamically provisioned by the MCP test server's `ensure_test_setup`).  
* **Local DBs**: Data storage access configured according to the Butler specification ([butler/](file:///home/peringknife/udmi/butler/)).  
* **Auth model**: Supported authentication methods are No Authentication (relying on network-level access control) and IAP (Identity-Aware Proxy).

## **Front-End Layer (GUMMI UI)**

* **Framework**: No framework. Vanilla JS only.  
* **State Management**:  
  * A lightweight client-side store to manage the fetched fleet data, active filters, and user session state.  
  * Backend caching database to improve performance for DB queries.  
* **Component Library**: Utilization of a standard UI kit (e.g., Material UI) to ensure a clean, professional aesthetic without bespoke styling overhead.

# **Agentic Workflow Guidance for UX Prototype**

To successfully generate and validate the prototype, the development agent should focus on the following priorities:

1. **Interactive Navigation**: Ensure seamless routing between the Dashboard, Explorer, and Detail views to maintain user context.  
2. **Data Density**: Strike a balance between technical depth (UDMI schemas) and operational readability for fleet managers; prioritize "at-a-glance" comprehension.  
3. **Simulated Interactions**: Implement functional 'Save' buttons for configuration changes that update the local or mock state to demonstrate the workflow loop to users during demos.  
4. **Error Handling Representation**: Design visual states for "Offline" devices or "Schema Violations" to test how operators react to system failures.  
5. **E2E Testing with Playwright**: Implement and run end-to-end browser tests using **Playwright** to verify UI page routing, responsive table filtering/pagination, form interactions, and real-time Server-Sent Events rendering.  
6. **Test Infrastructure Management via MCP**: When running integration and Playwright tests against local backend services, use the **Test Infrastructure MCP Server** (`bin/test_infra_mcp` / `bin/test_setup`) to orchestrate (`ensure_test_setup`), inspect (`list_test_windows`, `get_test_logs`), and teardown (`terminate_test_setup`) hermetic test environments.
7. **Testing Runbooks & Lifecycle Specs**: Refer to [**`gummi/SCENARIOS.md`**](file:///home/peringknife/udmi/gummi/SCENARIOS.md) for detailed test scenarios (mock UI, Playwright, bridgehead, live DUT), and [**`gummi/MAPPING.md`**](file:///home/peringknife/udmi/gummi/MAPPING.md) for the discovery and proposal lifecycle specification.

