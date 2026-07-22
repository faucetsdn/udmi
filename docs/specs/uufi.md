[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [UUFI](#)

# Unified UDMI Functional Interface (UUFI)

The **Unified UDMI Functional Interface (UUFI)** defines a standardized messaging mechanism for external applications (the **Client**) to integrate with a UDMI-managed system (the **System**). UUFI serves exclusively as an **external-facing, application-side interface** to UDMI, enabling management and control of devices.

As an external interface specification, this document focuses solely on the public-facing contract: the exchange message formats, connectivity schemas, and the tools available to start or stop test environments. It **does not expose or document the underlying system internals** or implementation mechanisms (such as process IDs, internal databases, or local host execution steps), which are treated as private implementation details.

## System Interfaces

This document details the application-facing **[Applications (UUFI)](uufi.md)** interface, which represents one of three core system interfaces into the UDMIS ecosystem:
*   **[Devices (UDMI proper)](compliance.md):** Edge-side device-to-system interface used by physical or simulated on-premise hardware to report telemetry streams (`events`) and dynamically receive or acknowledge operational `config`.
*   **[Internal Tools (Reflector)](tech_stack.md):** Administrative database-to-system interface used by backend registration and synchronization tooling (such as `registrar`) to manage site models, provision cryptographic keys, and reconcile device records.
*   **[Applications (UUFI)](uufi.md):** App-to-system messaging interface used by external applications, dashboards, and operators to query system state, update model specifications, and command devices.

## 1. Architecture

UUFI utilizes a messaging transport where Clients and Systems interact via dedicated topics and subscriptions.

### Message Flow
- **Publish (to System):** The Client publishes a UUFI-encapsulated UDMI message.
- **Receive (from System):** The System delivers UUFI-encapsulated UDMI messages to the Client.

## 2. Connectivity

### 2.1. Connection String
UUFI interfaces use a URL-like connection string format. Supported schemes: `mqtt://`, `mqtts://`, and `pubsub://`.

Format: `scheme://[user@]host[:port][/path]`

- **Default User:** `unknown`
- **User Separation:** The `@` character is required if a `user` is specified.
- **Default Port:** Protocol-specific.

### 2.2. Protocol Mapping

#### PubSub (`pubsub://`)
- **Host:** GCP Project ID.
- **User:** Maps to a subscription suffix and the `principal` attribute.
- **Principal:** The `user` component with a trailing `@` (e.g., `user@`).
- **Path:** First component maps to the root topic name (default: `udmi_uufi`).
- **Subscription:** `{topic}+{user}`.
- **Filtering:** Subscriptions should filter for messages where the `principal` attribute matches the local identity or is absent.
- **Constraint:** `:port` is prohibited.
- **Authentication:** Authentication and authorization for the `pubsub://` transport is managed out-of-band using standard Cloud IAM and Application Default Credentials (ADC). The connection string specifies the project and topic routing coordinates, whereas credential resolution is a transport-level prerequisite.

#### MQTT and MQTTS (`mqtt://`, `mqtts://`)
- **Host/Port:** Standard network mapping.
- **SSL/TLS & Mutual Authentication:** When a secure connection is requested (via the `mqtts://` scheme, or secure ports like `8883`):
  - The connection MUST utilize SSL/TLS with mutual certificate-based authentication (mTLS) where applicable.
  - Implementations MUST treat `mqtts://` with the same routing, parsing, and topic mapping rules as `mqtt://`, differing only in the enforcement of SSL/TLS.
- **Topic Structure:** `[/{prefix}]/uufi/[r/{deviceRegistryId}/[d/{deviceId}/]]c/{subType}/{subFolder}`
  - **Rigid Segment Presence:** Every UUFI topic path MUST include both a subtype (`subType`) and a subfolder (`subFolder`) segment without exception. Topic paths MUST be formatted strictly as `[/{prefix}]/uufi/c/{subType}/{subFolder}` (for common channels) or `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/{subType}/{subFolder}` (for registry/device scoped channels). Omitting the subfolder segment or truncating suffixes (e.g. `/c/{subType}`) is strictly prohibited.
  - **Handshake Subfolder Suffix:** For standard registry-less handshakes, the subfolder segment MUST be explicitly set to `"udmi"`. Thus, the handshake topics MUST be exactly `[/{prefix}]/uufi/c/state/udmi` and `[/{prefix}]/uufi/c/config/udmi`.
  - **Prohibition of Alternative Topic Structures:** Principal-scoped patterns (such as `[/{prefix}]/uufi/p/{principal}/...`) and any other custom or arbitrary routing hierarchies are strictly prohibited. All system components MUST standardize exclusively on the common and registry-scoped topic channels to ensure interoperability.
- **Prefix Isolation:** The `prefix` MUST be used to isolate different environments sharing the same broker. If provided, it MUST be the leading part of the topic path (e.g. matching all segments of the path provided in the connection string). Implementations MUST support multi-segment prefixes and MUST NOT omit the prefix if provided in the connection string. All active subscriptions MUST be scoped to the provided prefix to ensure environmental isolation. Prefix enforcement MUST be strict: implementations MUST NOT publish to or subscribe from topics outside their designated prefix tree. To avoid collisions and support standard broker authorization structures, implementations MUST construct unique MQTT Client IDs adhering to a standardized format:
  - **Standardized Client ID Format:** Client IDs MUST be formatted strictly as `[/{prefix}]/{registry_id}/{client_id}`. If the registry is unknown or not applicable, the Client ID MUST be formatted as `[/{prefix}]/{client_id}`.
  - **Uniqueness and Nonces:** To ensure absolute uniqueness and prevent session hijacking or connection drops when multiple clients share a broker, implementations MAY append a random, unique alphanumeric suffix (e.g., `_sess123` or a secure random nonce) to the formatted Client ID.
- **Project Identity:** For the MQTT transport, the `projectId` field in the envelope SHOULD be treated as a general environment or project identifier. All components within a single UUFI session MUST use a consistent `projectId` (default: `vibrant`) to avoid ambiguity in message processing.
- **System Model Service:**
  - **Query:** Clients publish a `query/system` message to `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/query/system`.
  - **Response (Model Reply):** System publishes the device system model to `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/model/system`.
  - **Structure:** Uses standard flat **SystemModel** payloads addressed via device-scoped envelope attributes and topic paths (Section 5.1).
- **State Query Service:**
  - **Query:** Clients publish a `query/state` message to `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/query/state`.
  - **Response:** System immediately replies by publishing the last known cached device State report on the device's state topic `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/state/blobset` (or the corresponding state topic matching the query's subFolder).
  - **Correlation & Timeout:** The query envelope MUST include a unique `transactionId`. The resulting state response envelope MUST copy this exact `transactionId` in its envelope metadata. If no response is received within 15 seconds, the Client SHOULD consider the state query timed out.
- **Telemetry and Event Service:**
  - **Telemetry (Pointset):** Devices or clients publish pointset telemetry reports to `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/events/pointset`.
  - **Discovery:** Devices or clients publish discovery reports to `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/events/discovery`.
  - **Structure:** Event payloads MUST conform strictly to their respective authoritative UDMI schemas (`pointset.json` or `discovery.json`) nested within the standard `payload` key for MQTT or the root of the message data object for PubSub.
- **System Status and Error Service:**
  - **Status Events:** The System and devices report runtime errors, command validation failures, or schema violation messages on the status channel: `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/events/status`.
  - **Structure:** Payload MUST conform strictly to the standard UDMI `status` / `Entry` schema (conforming to `entry.json`), containing `category`, `level`, `message`, and optionally `timestamp`.
  - **Correlation:** When status events are published in response to a failed client command, the envelope MUST copy the `transactionId` from the preceding client message to enable exact error correlation.

### 2.3. Resource Identifier Constraints
To ensure compatibility with MQTT topic parsing structures and PubSub attribute indexing, all identifier strings utilized in UUFI connection coordinates and paths MUST conform to the following character and length restrictions:
- **Allowed Characters:** Registry IDs (`{deviceRegistryId}`) and Device IDs (`{deviceId}`) MUST contain only uppercase and lowercase alphanumeric characters, hyphens (`-`), and underscores (`_`).
- **Length Limit:** Identifiers MUST be between 1 and 256 characters in length.
- **Prohibited Characters:** Spaces, slashes (`/`), backslashes (`\`), wildcards (`+`, `#`), and special punctuation characters are strictly prohibited.

## 3. Handshake Protocol

Handshake is **Client-initiated**. The **System MUST NOT initiate a handshake** unless acting as a Client.

The handshake has a 60-second timeout. On timeout, the Client should handle the failure and retry or fail-fast.

### 3.1. Handshake Steps and Payload Standards

To guarantee parsing interoperability and avoid protocol timeouts, Handshake payloads and correlation MUST strictly adhere to the following rules:

- **Single, Strict, and Flattened Payload Structure:**
  - **Step 1 (Handshake Request):** The `"setup"` payload block MUST reside directly at the payload root of the message (i.e. the root of the inner UDMI state payload). Nested or wrapped formats (e.g., nesting `"setup"` under a `"udmi"` key or any other custom outer wrapper) are strictly prohibited.
  - **Step 2 (Handshake Response):** The `"reply"` payload block (along with the optional/mandatory `"setup"` block) MUST reside directly at the payload root of the message. Wrapping under a `"udmi"` root sub-object or any other custom outer wrapper is strictly prohibited.

- **Transaction Correlation:** 
  To ensure reliable request-response correlation on shared handshake channels (e.g., `/uufi/c/config/udmi`), the handshake configuration reply message's envelope MUST include a `"transactionId"` attribute matching the exact transaction ID from the client's handshake request envelope. Receivers MUST validate this correlation and reject mismatched responses.

- **Functional Capability Negotiation (`functions_ver`):**
  The `functions_ver` integer specified under the `setup` payload block represents the supported functional capability set of the client/system interface. If the System's maximum supported version is lower than the Client's requested version, the System MUST respond with its highest supported version in the Step 2 reply. Clients MUST gracefully handle this negotiation and determine whether to operate in fallback/degraded mode or terminate the session based on the negotiated `functions_ver`.

### Step 1: State Declaration (Handshake Request)
The Client publishes a UDMI `state` message to `/uufi/c/state/udmi`.
- **Payload:** Must include `setup` directly at the root (see Appendix A.1.1).
- **Addressing:** Registry-less topic. `source` in envelope contains Client identity.

### Step 2: Configuration Confirmation (Handshake Response)
The System publishes a UDMI `config` message to `/uufi/c/config/udmi`.
- **Payload:** Must include `setup` and `reply` directly at the root (see Appendix A.1.1.a and A.1.2.a).
- **Addressing:** Envelope `principal` MUST match Client's identity. For handshake replies, the System MUST use the `principal` or `source` from the received state message to ensure the reply reaches the correct client. If the received message has a `principal`, it SHOULD be used; otherwise, the `source` SHOULD be used as a fallback.

**Retries:** The Client SHOULD periodically republish the Step 1 state message (e.g., every 5 seconds) if a valid Step 2 confirmation has not been received, until the 60-second timeout.

**Activation:** The Client is **Active** when `reply.transaction_id` matches the original `state.setup.transaction_id`.

### 3.2. Registry ID Discovery
- **Default:** `default`
- **Discovery:** The System MAY provide a `deviceRegistryId` in the `config.udmi` handshake reply to the Client. To ensure interoperability, the `deviceRegistryId` SHOULD be placed within the `setup` block of the payload. The Client SHOULD use this `deviceRegistryId` for all subsequent registry-scoped topics. The System MUST NOT expect to discover its own `deviceRegistryId` from Client-initiated handshakes. To prevent collisions in multi-registry environments where device IDs may not be globally unique, the Client identity (`source` in envelope and `msg_source` in setup payload) SHOULD be a structured identifier in the format `{registry_id}/{device_id}` if the client already has knowledge of its designated registry. Otherwise, if the registry is unknown, the Client identity is the bare `{device_id}`, and the System will assign a default registry ID (e.g., `default`). (Note: Use `deviceRegistryId` camelCase exactly as specified; case-insensitive or snake_case matching is NOT guaranteed).

## 4. Message Encapsulation

All messages are wrapped in a UUFI Envelope.

### Mandatory Payload Fields
Inner JSON `payload` object MUST include:
- `timestamp`: RFC 3339 (minimal precision).
- `version`: UDMI schema version.

### Transport Mapping

| Transport | Envelope Location | Payload Location |
| :--- | :--- | :--- |
| **PubSub** | Message Attributes | Message Data (JSON) |
| **MQTT** | JSON Wrapper | Payload `payload` key |

#### MQTT Constraints
- **Redundancy:** Envelope fields MUST NOT include data encoded in the topic path (`subType`, `subFolder`, and if present, `deviceRegistryId`, `deviceId`).
- **Mandatory Envelope Fields:**
  - **Outgoing Messages:** Outgoing messages from compliant systems and verifiers MUST populate all standard envelope fields (`projectId`, `transactionId`, `publishTime`, `source`, `principal`, and `payload`).
  - **Incoming Messages:** Incoming messages received from external or upstream services/utilities MUST be parsed gracefully with robust fallback handling (e.g., defaulting a missing `"projectId"` to `"vibrant"` or the active project/prefix, and treating a missing `"principal"` as absent or falling back to `"source"`).
- **Nesting:** UDMI message data MUST be nested within the `payload` key.

### 4.1. Envelope Metadata and Configuration Attributes

To ensure protocol compatibility, data integrity, and protection against replay attacks, the following metadata policies are codified for UUFI message envelopes and payloads:

- **Envelope Nonce Attribute:**
  To support message deduplication and replay protection, clients and devices publishing state, event, or model messages SHOULD include a `"nonce"` field in the envelope's root containing a secure, pseudorandomly generated hexadecimal string (minimum 32 characters, e.g., 16 bytes of entropy).
  All receivers MUST gracefully accept, parse, and process envelopes containing the `"nonce"` attribute without throwing schema errors or dropping the messages.

- **Configuration Version Attribute:**
  All configuration envelopes (at the envelope metadata level) and their inner payloads (at the payload root level) MUST include a standard `"version"` attribute matching the UDMI version schema (e.g., `"1.5.2"`).
  Receivers MUST parse and validate this version attribute to guarantee protocol compatibility. Any configuration message that lacks this attribute or contains an invalid or unparseable version schema MUST be treated as non-compliant, rejected immediately, and failed.

## 5. System Model Operations

### 5.1. Schema and Addressing
- **Operation:** `READ`, `CREATE`, `UPDATE`, `DELETE`, `BIND`, `UNBIND`.
- **Addressing (Topic & Envelope Attributes):**
  To ensure consistent routing and avoid parsing complexity, all system model updates, queries, and replies MUST NOT use a nested JSON registries hierarchy (such as `"registries"` or `"devices"`) inside the payload. Instead, the registry ID and device ID MUST be specified strictly as message attributes (envelope level) or MQTT topic path segments:
  - **MQTT Topic:** `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/model/system`
  - **PubSub Attributes:** Envelope attributes MUST include `deviceRegistryId` and `deviceId` (along with `subFolder` set to `"system"` and `subType` set to `"model"` or `"query"`).
- **Payload Structure:**
  The inner message payload MUST follow the flat, unnested `SystemModel` schema representing only the specified device (conforming directly to `model_system.json`).
- **Prohibited Formats:** 
  - Nested payload hierarchies (e.g., nesting under a `"registries"`, `"devices"`, or `"system"` root key inside the JSON payload) are strictly prohibited.
  - Sourcing or publishing system model updates from `/uufi/c/config/system` or any other unscoped/registry-less topics is strictly prohibited.

### 5.2. Update Semantics (Partial Merge)
The `UPDATE` operation for the `system` subfolder is a partial merge at the device subsystem level. Existing fields not in the payload MUST NOT be modified.

### 5.3. Device System Configuration
To configure a device's expected or desired software subsystem version, implementations MUST adhere to exactly ONE standard schema:
- **Standard Expected Version Path:** The expected version MUST be configured exclusively within the standard software dictionary structure under system configuration: `system.software.<subsystem> = "{version}"` (where `<subsystem>` defaults to `"system"`).
- **Prohibition of Custom Properties:** Any custom, flat, or alternative properties (such as `system.target_version` or `system.software_target`) are strictly prohibited and MUST NOT be accepted.

### 5.4. Actual Version Reporting
To report a device's actual or currently running software subsystem version, implementations MUST adhere strictly to the standard UDMI schema:
- **Standard Actual Version Path:** The actual running version MUST be reported exclusively within the software dictionary under the system state payload root: `system.software.<subsystem> = "{version}"`.
- **Prohibition of Non-Standard State Paths:** Reporting actual running versions within alternative structures (such as trying to nest a `version` property under `blobset.blobs.<subsystem_id>`) is strictly prohibited.

## 6. UDMI to UUFI Mapping

| UDMI Operation | Envelope `subType` | Envelope `subFolder` | Direction | Note |
| :--- | :--- | :--- | :--- | :--- |
| Handshake State | `state` | `udmi` | Publish | Standard flat format (Step 1) |
| Handshake Config | `config` | `udmi` | Receive | Standard flat format (Step 2) |
| Config Update | `config` | *varies* | Publish | |
| State Event | `state` | *varies* | Receive | |
| Telemetry | `events` | `pointset` | Receive | |
| Discovery | `events` | `discovery` | Receive | |
| System Model Query | `query` | `system` | Publish | Registry and device-scoped |
| System Model Update | `model` | `system` | Publish | Registry and device-scoped |
| System Model Reply | `model` | `system` | Receive | Registry and device-scoped |
| State Query | `query` | `state` | Publish | |
| Blobset Config | `config` | `blobset` | Publish | |
| Blobset State | `state` | `blobset` | Receive | |

## 7. Reliability

### MQTT QoS
- **Requirement:** QoS 1 (At Least Once) for all state and configuration messages.

### Idempotency
- **Transaction ID:** MUST use a unique `transactionId` for message identification.
- **Deduplication:** Track `transactionId`s for 5 minutes.

## 8. Payload and Formatting Rules

### 8.1. Payload Structure
- **Nesting:** The `payload` object contains the fields of the UDMI message corresponding to the `subFolder` and `subType`.
- **Subsystem Nesting:** For `blobset` config and state payloads, data MUST be nested within a subsystem-id key (e.g., `system`) to support multi-subsystem devices. Subsystem nesting is strictly required for all UUFI-compliant messages; unnested (flat) payloads are not supported.
- **Mandatory Fields:** `timestamp` and `version` MUST be at the root of the `payload` object.
- **Metadata:** The `make` and `model` fields are mandatory for all `blobset` subfolder payloads (state and config) within the subsystem nesting.
- **Blobset Config URL:** The `url` field in a `blobset` config payload MUST be a valid URI. Implementations MUST support absolute and relative path mapping using the `file://` scheme, as well as inline payloads using the `data:` scheme:
  - **Absolute Paths:** If the URI has three leading slashes (e.g., `file:///var/tmp/bundle.bin`), the scheme is stripped and resolved as an absolute path (e.g., `/var/tmp/bundle.bin`).
  - **Relative Paths:** If the URI has two leading slashes (e.g., `file://relative/path/bundle.bin`), the scheme is stripped and resolved as a path relative to the designated application or site-model root.
  - **Inline Data Payloads:** If the URI uses the `data:` scheme (e.g., `data:text/plain;base64,MS4xLjA=` or `data:application/json;base64,...`), implementations MUST parse the MIME-type and decode the trailing base64 segment (the portion immediately following `;base64,`) to retrieve the raw bytes.

### 8.2. Timestamp Format
- **Format:** RFC 3339 minimal precision (e.g., `2026-05-01T22:32:17Z`).
- **Timezone:** UTC required (`Z` suffix).
- **Precision:** System-originated messages SHOULD NOT include fractional seconds. Clients MAY include fractional seconds (microseconds), and all implementations MUST handle them gracefully by ignoring extra precision if necessary.

### 8.3. Type Safety and Fallbacks
- **Type Safety:** Mandatory version strings (`current_version`, `target_version`, etc.) MUST NOT be `null`. If a version is unknown, use a placeholder string like `"0.0.0"`. Implementations MUST treat `"0.0.0"` as an uninitialized or lower-precedence state; a non-zero version string MUST NEVER be overwritten by `"0.0.0"` during automated synchronization.
- **Metadata Fallbacks:** For mandatory string fields like `make` and `model`, if the value is unknown or uninitialized, implementations SHOULD use `"unknown"` as a standard fallback value.

### 8.4. MQTT Specific Rules
- **Redundancy Rule:** Implementations MUST reject messages where envelope fields duplicate topic-encoded data.
- **Leading Slash:** For MQTT transport, all UUFI topics MUST start with a leading slash `/`. Implementations MUST NOT accept or publish to topics lacking the leading slash.

## 9. Test Setup for External Clients

This section specifies how external client developers (working outside the UDMI project codebase) can set up a local testing environment to build and verify custom UUFI Client implementations against a running System and Device Under Test (DUT).

### 9.1. Local System Infrastructure (`bin/start_local`)
To initialize the local System stack (Mosquitto broker, etcd, and UDMIS control plane), execute the local startup script with a designated target site model directory and connection spec:

```bash
bin/start_local [site_model_dir] //mqtt/localhost:$port
```

Example:
```bash
bin/start_local sites/udmi_site_model //mqtt/localhost:46432
```

- **Format Constraint:** The target connection spec MUST be provided in the format `//mqtt/localhost:$port`. This satisfies internal parameter expansions (`${project_spec%/*}` and `${project_spec#//}`) within `start_local`, permitting custom isolated port execution without modifying UDMI binaries.
- **Shutdown & Lifecycle Provisions:** Service processes are managed directly via the tooling rather than manual process matching or `kill`/`pkill` commands. To stop the local system infrastructure, execute `bin/start_local stop`. Re-executing `bin/start_local` automatically stops and cleans up any existing background instances before starting fresh.

Upon successful startup, the local environment exposes the following connection parameters:
- **Broker Endpoint:** `mqtt://localhost:46432` (or `ssl://localhost:46432` for mTLS)
- **Authentication Credentials:** Username `rocket`, Password `monkey`
- **mTLS Certificates:**
  - CA Certificate: `sites/udmi_site_model/reflector/ca.crt`
  - Client Certificate: `sites/udmi_site_model/reflector/rsa_private.crt`
  - Private Key: `sites/udmi_site_model/reflector/rsa_private.pem`

### 9.2. Provisioning a Device Under Test (DUT) (`bin/start_dut`)
To emulate a managed device executing commands and reporting telemetry over UUFI, launch the DUT (Pubber) process targeting the site model and connection endpoint:

```bash
bin/start_dut <site_model_dir> //mqtt/localhost:$port [device_id] [serial_no]
```

Example:
```bash
bin/start_dut sites/udmi_site_model //mqtt/localhost:46432 AHU-1 uufi-serial
```

- **Format Constraint:** The second positional argument MUST begin with `//` (e.g. `//mqtt/localhost:$port`). Omitting `//` or passing explicit schemes like `mqtts://` causes internal initialization (`reset_config`) to select `iot_provider="jwt"`, resulting in a fatal runtime error (`Unknown iotProvider jwt`).
- **Function:** Registers device `AHU-1` (or specified device ID) with serial number `uufi-serial` and begins standard message exchange between the DUT and the local System.
- **Shutdown Provision:** To stop a running DUT process without manual process manipulation, invoke the `stop` subcommand with the target serial number:
  ```bash
  bin/start_dut stop <serial_no>
  ```
- **Lifecycle Management:** Launching `bin/start_dut` with the serial number of an active DUT automatically stops the existing instance before launching the new process.

### 9.3. Database & Tagabase Registration (`bin/registrar`)
Prior to test execution, the database synchronization step must be executed using the standard registrar tool in full online mode:

```bash
bin/registrar <site_model_dir> //mqtt/localhost:$port
```

Example:
```bash
bin/registrar sites/udmi_site_model //mqtt/localhost:46432
```

- **Function:** Synchronizes the site model database, generates `metadata_norm.json`, populates `generated_config.json`, and initializes tagabase entries for simulated devices.

### 9.4. Site Model Database Mutations (`bin/site_trigger`)
To trigger automated database mutations or field updates during testing, execute `bin/site_trigger` using explicit connection syntax:

```bash
bin/site_trigger update <site_path> <device_id> <blob_id> <version> //mqtt/localhost:$port
```

Example:
```bash
bin/site_trigger update sites/udmi_site_model AHU-1 system 1.0.0 //mqtt/localhost:46432
```

- **Format Constraint:** Enforces `//mqtt/localhost:$port` syntax as the final argument to target custom ports correctly.

### 9.5. Hermetic Environment Teardown
To tear down or stop running test infrastructure and DUT simulations in isolated or non-privileged environments, use the provided high-level tool commands:

- **System Infrastructure Teardown:** Stop all local infrastructure services (such as the broker, etcd, influxd database, and registry plane) using the stop subcommand on the local startup service:
  ```bash
  bin/start_local stop
  ```
- **DUT Simulation Teardown:** Stop individual emulated devices using the stop subcommand followed by the device's unique identifier or serial number:
  ```bash
  bin/start_dut stop <serial_no>
  ```
- **Execution Policy:** External test orchestrators MUST rely exclusively on these high-level stop commands to tear down the environment. Manual process termination or global system-wide commands are unsupported and may result in partial teardowns or configuration corruption.

### 9.6. Non-Privileged Execution Trigger
* **Trigger Mechanism:** Non-privileged execution mode MUST be automatically enabled whenever an explicit, custom MQTT port is specified in the connection string (e.g., `//mqtt/localhost:<port>`).
* **Environment-Free Bypasses:** The use of `UDMI_NO_SUDO` is deprecated. Instead, any port specification other than system-default ports (or any explicit port for local execution) automatically instructs all scripts to run in user-space.
* **User-Space Relocation:** When non-privileged mode is triggered, the system operates entirely in user-space without attempting system-wide directory modifications or system service management (e.g., `systemctl`). Instead, the broker is started as a local user background process utilizing configuration and log directories located inside the local workspace relative to the UDMI root.

---

# Appendix A: Schemas and Examples

This appendix references the formal JSON schemas and provides message examples for the UUFI protocol. The **UDMI Schema Repository** is the authoritative source for all message structures.

## A.1. Examples

### A.1.1. Handshake (PubSub)

**Attributes:**
```json
{
  "subFolder": "udmi",
  "subType": "state",
  "transactionId": "UUFI:sess123:001",
  "source": "client-id",
  "principal": "client-id@"
}
```

**Data:**
```json
{
  "version": "1.5.2",
  "timestamp": "2026-04-29T10:00:00Z",
  "setup": {
    "functions_ver": 9,
    "transaction_id": "UUFI:sess123:001",
    "msg_source": "client-id"
  }
}
```

### A.1.1.a. Handshake Response (PubSub)

**Attributes:**
```json
{
  "subFolder": "udmi",
  "subType": "config",
  "transactionId": "UUFI:sess123:001",
  "source": "system-id",
  "principal": "client-id@"
}
```

**Data:**
```json
{
  "version": "1.5.2",
  "timestamp": "2026-04-29T10:00:05Z",
  "setup": {
    "functions_ver": 9,
    "transaction_id": "UUFI:sess123:001",
    "msg_source": "client-id"
  },
  "reply": {
    "transaction_id": "UUFI:sess123:001"
  }
}
```

### A.1.2. Handshake (MQTT)

**Topic:** `/uufi/c/state/udmi`

**Payload:**
```json
{
  "projectId": "vibrant",
  "transactionId": "UUFI:sess123:001",
  "publishTime": "2026-04-29T10:00:00Z",
  "source": "client-id",
  "principal": "client-id",
  "payload": {
    "version": "1.5.2",
    "timestamp": "2026-04-29T10:00:00Z",
    "setup": {
      "functions_ver": 9,
      "transaction_id": "UUFI:sess123:001",
      "msg_source": "client-id"
    }
  }
}
```

### A.1.2.a. Handshake Response (MQTT)

**Topic:** `/uufi/c/config/udmi`

**Payload:**
```json
{
  "projectId": "vibrant",
  "transactionId": "UUFI:sess123:001",
  "publishTime": "2026-04-29T10:00:05Z",
  "source": "system-id",
  "principal": "client-id",
  "payload": {
    "version": "1.5.2",
    "timestamp": "2026-04-29T10:00:05Z",
    "setup": {
      "functions_ver": 9,
      "transaction_id": "UUFI:sess123:001",
      "msg_source": "client-id"
    },
    "reply": {
      "transaction_id": "UUFI:sess123:001"
    }
  }
}
```

### A.1.3. Pointset Config (PubSub)

**Attributes:**
```json
{
  "subFolder": "pointset",
  "subType": "config",
  "transactionId": "UUFI:sess123:002",
  "source": "client-id",
  "principal": "client-id@",
  "deviceRegistryId": "reg-1",
  "deviceId": "dev-1"
}
```

**Data:**
```json
{
  "version": "1.5.2",
  "timestamp": "2026-04-29T10:05:00Z",
  "points": {
    "temp": { "set_value": 22.5 }
  }
}
```

### A.1.4. Pointset Config (MQTT)

**Topic:** `/uufi/r/reg-1/d/dev-1/c/config/pointset`

**Payload:**
```json
{
  "transactionId": "UUFI:sess123:002",
  "principal": "client-id",
  "payload": {
    "version": "1.5.2",
    "timestamp": "2026-04-29T10:05:00Z",
    "points": {
      "temp": { "set_value": 22.5 }
    }
  }
}
```

### A.1.5. Blobset Config (PubSub)

**Attributes:**
```json
{
  "subFolder": "blobset",
  "subType": "config",
  "transactionId": "UUFI:sess123:003",
  "source": "client-id",
  "principal": "client-id@",
  "deviceRegistryId": "reg-1",
  "deviceId": "dev-1"
}
```

**Data:**
```json
{
  "version": "1.5.2",
  "timestamp": "2026-04-29T10:10:00Z",
  "blobset": {
    "blobs": {
      "system": {
        "phase": "apply",
        "url": "file:///path/to/bundle.bin",
        "sha256": "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
        "generation": "2026-04-29T10:10:00Z"
      }
    }
  }
}
```

### A.1.6. Blobset Config (MQTT)

**Topic:** `/uufi/r/reg-1/d/dev-1/c/config/blobset`

**Payload:**
```json
{
  "transactionId": "UUFI:sess123:003",
  "principal": "client-id",
  "payload": {
    "version": "1.5.2",
    "timestamp": "2026-04-29T10:10:00Z",
    "blobset": {
      "blobs": {
        "system": {
          "phase": "apply",
          "url": "file:///path/to/bundle.bin",
          "sha256": "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
          "generation": "2026-04-29T10:10:00Z"
        }
      }
    }
  }
}
```

### A.1.7. System Model Update (PubSub)

**Attributes:**
```json
{
  "subFolder": "system",
  "subType": "model",
  "transactionId": "UUFI:sess123:004",
  "source": "orchestrator",
  "principal": "orchestrator",
  "deviceRegistryId": "reg-1",
  "deviceId": "dev-1"
}
```

**Data:**
```json
{
  "version": "1.5.2",
  "timestamp": "2026-04-29T10:15:00Z",
  "software": {
        "system": "2.1.0"
  }
}
```

### A.1.8. System Model Update (MQTT)

**Topic:** `/uufi/r/reg-1/d/dev-1/c/model/system`

**Payload:**
```json
{
  "projectId": "vibrant",
  "transactionId": "UUFI:sess123:004",
  "publishTime": "2026-04-29T10:15:00Z",
  "source": "orchestrator",
  "principal": "orchestrator",
  "payload": {
    "version": "1.5.2",
    "timestamp": "2026-04-29T10:15:00Z",
    "software": {
      "system": "2.1.0"
    }
  }
}
```

## A.2. Authoritative Schemas

UUFI implementations MUST adhere to the following schemas from the UDMI repository:

| UUFI Component | Authoritative UDMI Schema |
| :--- | :--- |
| **Message Envelope** | `envelope.json` |
| **Handshake State** | `state_udmi.json` |
| **Handshake Config** | `config_udmi.json` |
| **System Model** | `model_system.json` |
| **Blobset Config** | `config_blobset.json` |
| **Blobset State** | `state_blobset.json` |
