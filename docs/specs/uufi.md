[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [UUFI](#)

# Unified UDMI Functional Interface (UUFI)

The **Unified UDMI Functional Interface (UUFI)** defines a standardized messaging mechanism for external applications (the **Client**) to integrate with a UDMI-managed system (the **System**). This needs to be said: this provides a 'Unified Universal Device Management Interface Functional Interface'. This
is essentially an application-side interface into UDMI in contrast to the on-prem device side interface... similar but slightly different.

## 1. Architecture

UUFI utilizes a messaging transport where Clients and Systems interact via dedicated topics and subscriptions.

### Message Flow
- **Publish (to System):** The Client publishes a UUFI-encapsulated UDMI message.
- **Receive (from System):** The System delivers UUFI-encapsulated UDMI messages to the Client.

## 2. Connectivity

### 2.1. Connection String
UUFI interfaces use a URL-like connection string format. Supported schemes: `mqtt://` and `pubsub://`.

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

#### MQTT (`mqtt://`)
- **Host/Port:** Standard network mapping.
- **Topic Structure:** `[/{prefix}]/uufi/[r/{deviceRegistryId}/[d/{deviceId}/]]c/{subType}/{subFolder}`
  - The `prefix` is the optional path component of the connection string, representing one or more path segments. Implementations MUST strip any leading or trailing slashes from the path component before using it as a `prefix`. In UDMI environments, the `prefix` often corresponds to the `UDMI_PREFIX` environment variable, which isolates multiple UDMI installations on the same messaging backbone.
  - **Rigid Segment Presence:** Every UUFI topic path MUST include both a subtype (`subType`) and a subfolder (`subFolder`) segment without exception. Topic paths MUST be formatted strictly as `[/{prefix}]/uufi/c/{subType}/{subFolder}` (for common channels) or `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/{subType}/{subFolder}` (for registry/device scoped channels). Omitting the subfolder segment or truncating suffixes (e.g. `/c/{subType}`) is strictly prohibited.
  - **Handshake Subfolder Suffix:** For standard registry-less handshakes, the subfolder segment MUST be explicitly set to `"udmi"`. Thus, the handshake topics MUST be exactly `[/{prefix}]/uufi/c/state/udmi` and `[/{prefix}]/uufi/c/config/udmi`.
  - **Prohibition of Alternative Topic Structures:** Principal-scoped patterns (such as `[/{prefix}]/uufi/p/{principal}/...`) and any other custom or arbitrary routing hierarchies are strictly prohibited. All system components MUST standardize exclusively on the common and registry-scoped topic channels to ensure interoperability.
- **Prefix Isolation:** The `prefix` MUST be used to isolate different environments sharing the same broker. If provided, it MUST be the leading part of the topic path (e.g. matching all segments of the path provided in the connection string). Implementations MUST support multi-segment prefixes and MUST NOT omit the prefix if provided in the connection string. All active subscriptions (including those for traffic observation) MUST be scoped to the provided prefix to ensure environmental isolation. Prefix enforcement MUST be strict: implementations MUST NOT publish to or subscribe from topics outside their designated prefix tree. To avoid collisions and support standard broker authorization structures, implementations MUST construct unique MQTT Client IDs adhering to a standardized format:
  - **Standardized Client ID Format:** Client IDs MUST be formatted strictly as `[/{prefix}]/{registry_id}/{client_id}`. If the registry is unknown or not applicable, the Client ID MUST be formatted as `[/{prefix}]/{client_id}`.
  - **Uniqueness and Nonces:** To ensure absolute uniqueness and prevent session hijacking or connection drops when multiple clients share a broker, implementations MAY append a random, unique alphanumeric suffix (e.g., `_sess123` or a secure random nonce) to the formatted Client ID.
- **Project Identity:** For the MQTT transport, the `projectId` field in the envelope SHOULD be treated as a general environment or project identifier. All components within a single UUFI session MUST use a consistent `projectId` (default: `vibrant`) to avoid ambiguity in message processing.
- **Cloud Model Service:**
  - **Discovery (Query):** Clients publish a `query/cloud` message to `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/query/cloud`.
  - **Response (Model Reply):** System publishes the device cloud model to `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/model/cloud`. Sourcing model updates from `[/{prefix}]/uufi/c/config/cloud` or other un-scoped/registry-less topics is strictly prohibited.
  - **Structure:** Uses standard flat **CloudModel** payloads addressed via device-scoped envelope attributes and topic paths (Section 5.1).
- **State Query Service:**
  - **Discovery:** Clients publish a `query/state` message to `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/query/state`.
  - **Response:** System (the gateway/processor caching the state) immediately replies by publishing the last known cached device State report on the device's state topic `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/state/blobset` (or the corresponding state topic matching the query's subFolder).

## 3. Handshake Protocol

Handshake is **Client-initiated**. The **System MUST NOT initiate a handshake** unless acting as a Client.

- **Wait for Handshake**: The System SHOULD wait for at least one Client handshake before becoming fully active, but it MUST NOT block indefinitely if no Clients are present.
- **Local Blobs**: For local file references, the `url` MUST use the `file://` scheme. Recipients MUST strip the scheme to resolve the path.
- **Metadata**: Device metadata (`make`, `model`) SHOULD be stored in a dedicated `meta` subsystem within the cloud model for consistency.

### 3.1. Handshake Steps and Payload Standards

To guarantee parsing interoperability and avoid protocol timeouts, Handshake payloads and correlation MUST strictly adhere to the following rules:

- **Single, Strict, and Flattened Payload Structure:**
  - **Step 1 (Handshake Request):** The `"setup"` payload block MUST reside directly at the payload root of the message (i.e. the root of the inner UDMI state payload). Nested or wrapped formats (e.g., nesting `"setup"` under a `"udmi"` key or any other custom outer wrapper) are strictly prohibited and MUST be treated as protocol violations.
  - **Step 2 (Handshake Response):** The `"reply"` payload block (along with the optional/mandatory `"setup"` block) MUST reside directly at the payload root of the message. Wrapping under a `"udmi"` root sub-object or any other custom outer wrapper is strictly prohibited and MUST be treated as a protocol violation.

- **Transaction Correlation:** 
  To ensure reliable request-response correlation on shared handshake channels (e.g., `/uufi/c/config/udmi`), the handshake configuration reply message's envelope MUST include a `"transactionId"` attribute matching the exact transaction ID from the client's handshake request envelope. Receivers MUST validate this correlation and reject mismatched responses.

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

### Registry ID Discovery
<!-- ASSUMPTION: User direct command overrides the general spec edit restrictions of AGENTS.md -->
- **Default:** `default`
- **Discovery:** The System MAY provide a `deviceRegistryId` in the `config.udmi` handshake reply to the Client. To ensure interoperability, the `deviceRegistryId` SHOULD be placed within the `setup` block of the payload. The Client SHOULD use this `deviceRegistryId` for all subsequent registry-scoped topics. The System MUST NOT expect to discover its own `deviceRegistryId` from Client-initiated handshakes. To prevent collisions in multi-registry environments where device IDs may not be globally unique, the Client identity (`source` in envelope and `msg_source` in setup payload) SHOULD be a structured identifier in the format `{registry_id}/{device_id}` if the client already has knowledge of its designated registry. Otherwise, if the registry is unknown, the Client identity is the bare `{device_id}`, and the System will assign a default registry ID (e.g., `default`). (Note: Use `deviceRegistryId` camelCase exactly as specified; case-insensitive or snake_case matching is NOT guaranteed).
- **Responsiveness:** MQTT message callback handlers MUST NOT perform long-running or blocking operations (e.g., `time.sleep()`). Any simulated work or heavy processing MUST be offloaded to a separate thread to maintain system-wide responsiveness and avoid buffer overflows or message loss in high-concurrency environments.

### 3.1 Interoperability Reminders
- **Metadata Persistence:** System components MUST ingest and cache `make` and `model` information from all available sources (registration, cloud updates, and state reports). Device simulators SHOULD include these fields in every state report to ensure consistency.
- **Blobset Config Keys:** Implementations MUST use standard UDMI keys in the `blobset` subfolder config payloads.
- **Topic Slashes:** All UUFI topics MUST start with a leading slash `/`. The `prefix` (if any) is the first segment after the slash.
- **Handshake Robustness:** Clients SHOULD periodically republish their handshake state until a valid reply is received. Systems SHOULD reflect the Client's `principal` in the handshake reply.
- **Window:** 60 seconds.
- **Failure:** On timeout, the Client MUST log a critical error and terminate (Fail-fast).

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
- **Mandatory Fields:** The MQTT envelope MUST include `projectId`, `transactionId`, `publishTime`, `source`, `principal`, and `payload`.
- **Nesting:** UDMI message data MUST be nested within the `payload` key.

### 4.1. Envelope Metadata and Configuration Attributes

To ensure protocol compatibility, data integrity, and protection against replay attacks, the following metadata policies are codified for UUFI message envelopes and payloads:

- **Envelope Nonce Attribute:**
  To support message deduplication and replay protection, clients and devices publishing state, event, or model messages SHOULD include a `"nonce"` field in the envelope's root containing a secure, pseudorandomly generated hexadecimal string (minimum 32 characters, e.g., 16 bytes of entropy).
  All receivers (including orchestrators, gateways, and verifiers) MUST gracefully accept, parse, and process envelopes containing the `"nonce"` attribute without throwing schema errors or dropping the messages.

- **Configuration Version Attribute:**
  All configuration envelopes (at the envelope metadata level) and their inner payloads (at the payload root level) MUST include a standard `"version"` attribute matching the UDMI version schema (e.g., `"1.5.2"`).
  Receivers MUST parse and validate this version attribute to guarantee protocol compatibility. Any configuration message that lacks this attribute or contains an invalid or unparseable version schema MUST be treated as non-compliant, rejected immediately, and failed.

## 5. Cloud Model Operations

### 5.1. Schema and Addressing
- **Operation:** `READ`, `CREATE`, `UPDATE`, `DELETE`, `BIND`, `UNBIND`.
- **Addressing (Topic & Envelope Attributes):**
  To ensure consistent routing and avoid parsing complexity, all cloud model updates, queries, and replies MUST NOT use a nested JSON registries hierarchy (such as `"registries"` or `"devices"`) inside the payload. Instead, the registry ID and device ID MUST be specified strictly as message attributes (envelope level) or MQTT topic path segments:
  - **MQTT Topic:** `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/model/cloud`
  - **PubSub Attributes:** Envelope attributes MUST include `deviceRegistryId` and `deviceId` (along with `subFolder` set to `"cloud"` and `subType` set to `"model"` or `"query"`).
- **Payload Structure:**
  The inner message payload MUST follow the flat, unnested `CloudModel` schema representing only the specified device (conforming directly to `model_cloud.json`).
- **Prohibited Formats:** 
  - Nested payload hierarchies (e.g., nesting under a `"registries"`, `"devices"`, or `"cloud"` root key inside the JSON payload) are strictly prohibited.
  - Sourcing or publishing cloud model updates from `/uufi/c/config/cloud` or any other un-scoped/registry-less topics is strictly prohibited.

### 5.2. Update Semantics (Partial Merge)
The `UPDATE` operation for the `cloud` subfolder is a partial merge at the device subsystem level. Existing fields not in the payload MUST NOT be modified.

### 5.3. Device System Configuration
To configure a device's expected or desired software subsystem version, implementations MUST adhere to exactly ONE standard schema:
- **Standard Expected Version Path:** The expected version MUST be configured exclusively within the standard software dictionary structure under system configuration: `system.software.<subsystem> = "{version}"` (where `<subsystem>` defaults to `"system"`).
- **Prohibition of Custom Properties:** Any custom, flat, or alternative properties (such as `system.target_version` or `system.software_target`) are strictly prohibited and MUST NOT be accepted by the cloud orchestrator or processed by devices.

## 6. UDMI to UUFI Mapping

| UDMI Operation | Envelope `subType` | Envelope `subFolder` | Direction | Note |
| :--- | :--- | :--- | :--- | :--- |
| Handshake State | `state` | `udmi` | Publish | Standard flat format (Step 1) |
| Handshake Config | `config` | `udmi` | Receive | Standard flat format (Step 2) |
| Config Update | `config` | *varies* | Publish | |
| State Event | `state` | *varies* | Receive | |
| Telemetry | `events` | `pointset` | Receive | |
| Discovery | `events` | `discovery` | Receive | |
| Model Query | `query` | `cloud` | Publish | Registry and device-scoped |
| Model Update | `model` | `cloud` | Publish | Registry and device-scoped |
| Model Reply | `model` | `cloud` | Receive | Registry and device-scoped (config/cloud is prohibited) |
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
- **Subsystem Nesting:** For `blobset` config and state payloads, data MUST be nested within a subsystem-id key (e.g., `system`) to support multi-subsystem devices. Subsystem nesting is strictly required for all UUFI-compliant messages, and unnested (flat) payloads are not supported.

- **Mandatory Fields:** `timestamp` and `version` MUST be at the root of the `payload` object.
- **Metadata:** The `make` and `model` fields are mandatory for all `blobset` subfolder payloads (state and config) within the subsystem nesting. These fields are essential for the System to locate the correct blob in the repository and MUST be included in every subsystem entry subject to reconciliation.
- **Blobset Config URL & Canonical Path Resolution:** The `url` field in a `blobset` config payload MUST be a valid URI. Implementations MUST support the `file://` scheme for local file references. When a `file://` URI is provided, the recipient MUST convert it to a canonical path according to the following strict rules:
  - **Absolute Paths:** If the URI has three leading slashes (e.g., `file:///var/tmp/bundle.bin`), the scheme `file://` is stripped and the remainder (`/var/tmp/bundle.bin`) MUST be resolved as a standard absolute path on the host system.
  - **Relative Paths:** If the URI has two leading slashes (e.g., `file://relative/path/bundle.bin`), the scheme `file://` is stripped and the path MUST be resolved relative to a designated application/site-model root directory.
  - Standardization of these resolution rules ensures consistent payload parsing across diverse host operating systems.


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
- **Wildcards:** Subscription wildcards (e.g., `/#`) MUST also adhere to the leading slash rule and MUST be scoped to the connection-defined prefix to ensure consistent topic matching across the prefix tree.

---

# 9. Test and Development

The UDMI repository provides a modular, multi-tier local development and testing workflow to isolate infrastructure, devices, and client applications.

## 9.1. Scope 1: Basic Local Setup (Infrastructure Only)

The first tier initializes a spec-compliant UUFI message broker and gateway processor. This establishes the bare-minimum messaging backbone without any active devices or tests, providing a clean black-box middleware layer for external applications.

### Starting the Infrastructure
```bash
bin/start_local
```

This script performs the following actions:
1.  **MQTT Broker:** Starts a local Mosquitto broker on port 8883 with SSL enabled.
2.  **Site Model:** Uses the provided site model (e.g., `sites/udmi_site_model`) and configures it for local MQTT use.
3.  **UDMIS:** Starts the `udmis` service with the `UufiProcessor` enabled to act as the UUFI gateway.

**Connection Details:**
- **Scheme:** `mqtt://`
- **Host:** `localhost`
- **Port:** `8883`
- **Username:** `rocket`
- **Password:** `monkey`
- **CA Certificate:** `sites/udmi_site_model/reflector/ca.crt`

---

## 9.2. Scope 2: Local Setup with Pubber DUT (Device Under Test)

The second tier builds on top of Scope 1 by registering and launching a simulated on-premise device under the UDMI schema framework. This provides an active device stream for testing, receiving configuration updates, and reporting back telemetry.

### Registering and Launching the Device
When running tests or manual sessions, the environment launches **Pubber** as the simulated Device Under Test (DUT). This client:
1. Connects to the local MQTT broker as a device.
2. Begins periodically publishing telemetry state (such as `pointset` events) on standard UDMI topics.
3. Listens for configuration payloads routed through the UUFI system gateway.

---

## 9.3. Scope 3: Verification with the UUFI Test Client

The third tier performs active end-to-end integration testing of the UUFI interface by introducing a low-level test client that exchanges messages with the Pubber DUT (from Scope 2) over the active infrastructure (from Scope 1).

### Running Automated UUFI Pipeline Verification
To verify the entire bidirectional pipeline (System Gateway -> Broker -> DUT -> Client), run:
```bash
bin/test_uufi
```

This command automatically orchestrates the following operations:
1. **System Initialization:** Confirms that the Scope 1 local services are healthy.
2. **DUT Lifecycle:** Registers and launches a **Pubber** device (Scope 2).
3. **Handshake Phase:** Starts a low-level `uufi_test_client` which completes the standard Step 1 and Step 2 UUFI Handshake over the broker.
4. **Bidirectional Exchange:** 
   - Sends a UUFI-wrapped configuration update to the DUT.
   - Verifies that the gateway correctly routes the update to the Pubber device.
   - Captures the corresponding telemetry state returning from the Pubber device to confirm successful processing.

For modular, manual client-side testing against an already running environment, the standalone script can be run directly:
```bash
bin/uufi_test_client
```

---

## 9.4. Passive Observation and Trace Analysis

The `bin/observe_uufi` tool is a utility that provides a passive, real-time view of all messaging traffic on the UUFI topic tree, without actively participating in handshakes or sending configurations.

### Running the Observer
```bash
bin/observe_uufi
```

The observer will:
1.  **Subscribe:** Connects to the local MQTT broker and subscribes to `/uufi/#`.
2.  **Display:** Outputs every message received sequentially to `stdout` in a raw, unbuffered line-by-line format (`{topic}: {payload}`), making it ideal for checking message boundaries and verifying format compliance.

---

## 9.5. Interactive Site Model Database Update Emulation

To emulate physical database mutations and trigger live model-update events, developers can use the `bin/site_trigger` utility. This allows verification of reactive system orchestrators (like Butler) sitting on top of the UUFI bus without modifying raw config files by hand.

### Running the Site Model Trigger
```bash
bin/site_trigger update <site_path> <device_id> <blob_id> <version> [conn_spec]
```

This tool:
1.  **Mutates Site Model:** Locates the specified `metadata.json` file in the site directory (e.g., `<site_path>/devices/{device_id}/metadata.json`) and atomically updates its expected version tag (`system.software.<blob_id> = <version>`).
2.  **Triggers Model Event:** Synthesizes and publishes a corresponding `model/cloud` Model Update message to `/uufi/c/model/cloud` on the broker, enabling reactive orchestrators to instantly sync.

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

### A.1.7. Cloud Model Update (PubSub)

**Attributes:**
```json
{
  "subFolder": "cloud",
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
  "auth_type": "RS256",
  "blocked": false,
  "config": {
    "system": {
      "software": {
        "system": "2.1.0"
      }
    }
  }
}
```

### A.1.8. Cloud Model Update (MQTT)

**Topic:** `/uufi/r/reg-1/d/dev-1/c/model/cloud`

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
    "auth_type": "RS256",
    "blocked": false,
    "config": {
      "system": {
        "software": {
          "system": "2.1.0"
        }
      }
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
| **Cloud Model** | `model_cloud.json` |
| **Blobset Config** | `config_blobset.json` |
| **Blobset State** | `state_blobset.json` |
