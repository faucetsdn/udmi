# Butler System Orchestrator

The **Butler** is a system orchestrator that manages device updates and model synchronization using the UUFI interface.

## 1. Role and Behavior

- **Orchestrator Behavior:** The Butler is the primary authority for the `lkg_version` in the cloud model and SHOULD NOT trust a device-reported `lkg_version` if it conflicts with a previously validated state.
- **Model Synchronization:** Upon receiving a device report indicating a successful update (status `success` or `quiescent`) where the `current_version` differs from the known model state, the Butler MUST update the cloud model's `current_version` along with the `lkg_version`.
- **Persistence:** The Butler MUST update the local model file (if configured via `BUTLER_MODEL_FILE`) whenever the cloud model state changes (e.g., upon successful device update or model update command).
- **Metadata Ingestion:** Orchestrators MUST ingest and cache `make` and `model` information from all available sources, including initial registration, cloud model updates, and device state reports.
- **Handshake Protocol:** The Butler SHOULD implement the handshake protocol as specified in the UUFI documentation (Section 3) to ensure a reliable connection to the System.
- **LKG Management:** The Orchestrator is the primary authority for the `lkg_version` in the cloud model and SHOULD NOT trust a device-reported `lkg_version` if it conflicts with a previously validated state.
- **Model Update Robustness:** Relying solely on the transient `success` state is discouraged; any terminal state reporting the new version SHOULD trigger a model synchronization.
- **Identity Differentiators:** Butler implementations SHOULD NOT detect or reject identities with multiple components (e.g., `user.toolname`) as "manual differentiators" if they are part of a standardized naming scheme for tool identification.

## 2. Standard Tooling CLI Interface

Butler implementations MUST support the following command-line interface:

### 2.1. bin/butler
- **Usage:** `bin/butler <conn_spec>`
- **Behavior:** Starts the system orchestrator (Butler).

### 2.2. bin/register
- **Usage:** `bin/register [registry_id] <device_id> [make] [model]`
- **Behavior:** Registers a device in the local model. If only one argument is provided, it MUST be treated as the `device_id`.

### 2.3. bin/trigger
- **Usage:** `bin/trigger [registry_id] <device_id> <subsystem_id> <version> <blob_path>`
- **Behavior:** Initiates an update process. Similar to `register`, it MUST support optional `registry_id`.

### 2.4. bin/setup
- **Usage:** `bin/setup <conn_spec>`
- **Behavior:** Ensures the local environment (e.g., MQTT broker) is ready for the given connection specification.

### 2.5. bin/mocket
- **Usage:** `bin/mocket <conn_spec> <registry_id> <device_id>`
- **Behavior:** Starts a mock device client that responds to UUFI handshakes and update configurations.

### 2.6. bin/verifier
- **Usage:** `bin/verifier <conn_spec>`
- **Behavior:** Starts the independent verification tool.

## 3. Standard Configuration Environment Variables

- **`BUTLER_CONN_SPEC`**: The default connection specification URL (e.g., `mqtt://localhost:1883`).
- **`BUTLER_MODEL_FILE`**: The path to the local JSON file representing the cloud model (default: `testing/model.json`).
- **`BUTLER_BLOBS_DIR`**: The base directory for the blob repository (default: `testing/blobs`).
- **`BUTLER_TIMEOUT`**: The timeout in seconds for the orchestrator to wait for a device to progress from the `pending` state before triggering a rollback (default: `60`).
- **`BUTLER_REGISTRY_ID`**: The default registry ID to use when not specified (default: `default`).

## 4. Local Repository Structure (Standardized)

To ensure that tools from different Butler implementations can interoperate within the same local workspace, the following directory and file structures are standardized.

### 4.1. Blob Repository
Blobs MUST be stored in a directory structure following this pattern:
`{base_dir}/{make}/{model}/{subsystem_id}/{version}/`

Each version directory MUST contain:
- `bundle.bin`: The binary blob content.
- `sha256.txt`: A text file containing the hex-encoded SHA-256 hash of `bundle.bin`.

### 4.2. Model Repository
The cloud model, when stored as a local JSON file, MUST follow the full schema defined in the UUFI Appendix (A.4), including the top-level `cloud` wrapper and the 3-level nesting within it (Registries -> Devices -> Subsystems).
