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

### 1.1. Layer 2 Message-Role Truth Table

Without adding any extra properties to `schema/envelope.json`, the tuple `{ source, principal, payload, control }` deterministically classifies every Layer 2 UUFI message (Layer 1 session bootstrap continues to use `/uufi/c/state/udmi` → `/uufi/c/config/udmi`):

| Direction | `principal` | `payload` | `control` | Payload Schema | Role & Receiver Behavior |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Client → System** (`source != "udmis"`) | `null` | `null` | *—* | *N/A* | **NOP / Invalid**: Dropped and logged without reply. |
| **Client → System** (`source != "udmis"`) | `null` | `!= null` | *—* | *N/A* | **Invalid (Missing Principal)**: Dropped and logged without reply (`principal` is mandatory on all client messages). |
| **Client → System** (`source != "udmis"`) | `!= null` | `null` | *—* | *None (`null`)* | **Query (Read)**: Fetch current `(subType, subFolder)` state/config/model and reply on the **exact same channel**. |
| **Client → System** (`source != "udmis"`) | `!= null` | `!= null` | `null` | `<subType>_<subFolder>.json` *(or `String`)* | **Write (Mutate)**: Validate and apply payload (or dispatch string sentinel to lower handler layer) and reply on the **exact same channel**. |
| **Client → System** (`source != "udmis"`) | `!= null` | `!= null` | `!= null` | *N/A* | **Invalid (Client Control)**: Dropped and logged **without reply** (prevents control ping-pong loops). |
| **System → Client** (`source == "udmis"`) | `null` | `null` | *—* | *N/A* | **NOP / Invalid**: Dropped and logged (System never emits a `null` payload). |
| **System → Client** (`source == "udmis"`) | `null` | `!= null` | `null` | `<subType>_<subFolder>.json` *(or `String`)* | **Stream (Broadcast)**: Unsolicited live telemetry, state, or model update (or string sentinel) with a fresh `transactionId`. |
| **System → Client** (`source == "udmis"`) | `null` | `!= null` | `!= null` | `control_uufi.json` | **Control Stream (Broadcast)**: Unsolicited broadcast control notice with a fresh `transactionId`. |
| **System → Client** (`source == "udmis"`) | `!= null` | `null` | *—* | *N/A* | **NOP / Invalid**: Dropped and logged (System never emits a `null` payload). |
| **System → Client** (`source == "udmis"`) | `!= null` | `!= null` | `null` | `<subType>_<subFolder>.json` *(or `String`)* | **Success Ack**: Applied/queried domain payload (or string sentinel from the lower layer); clears pending `transactionId`. |
| **System → Client** (`source == "udmis"`) | `!= null` | `!= null` | `!= null` | `control_uufi.json` | **Control Ack**: Control-plane reply on `transactionId` (`status.level < 500` interim progress keeps `transactionId` open; `status.level >= 500` terminal error fails `transactionId`). |

### 1.2. Control-Plane Payloads, String Sentinels & Error Channel Exclusion

- **Control-Plane Discriminator (`schema/control_uufi.json`):** System control-plane replies (interim progress or terminal errors) are emitted on the **exact same resource channel** (`subType`, `subFolder`, `deviceRegistryId`, `deviceId`) with identical envelope attributes (`source: "udmis"`, matching `principal` and `transactionId`) and a payload containing the unique top-level `"control"` object conforming to `schema/control_uufi.json`.
  - `control.status.level < 500` (e.g., `200` `INFO`, `300` `NOTICE`, `400` `WARNING`): interim progress or advisory notice; the client MUST keep the pending `transactionId` open.
  - `control.status.level >= 500` (e.g., `500` `ERROR`): terminal failure; the client MUST close and fail the pending `transactionId`.
  - Clients MUST NOT send payloads with a root `"control"` key; any client-originated `"control"` message is dropped without reply.
- **String Sentinel Transport:** Because all standard UDMI message schemas (including `control_uufi.json`) are strictly JSON objects, a bare JSON `String` payload is recognized at the UUFI transport layer as a sentinel value without overloading `null`. The UUFI protocol layer safely transports string sentinels between Client and System; specific token names and semantics are defined and validated by lower handler layers (unrecognized client strings fail fast with a `status.level = 500` Control Ack).
- **Exclusion of `events/error`:** Internal error channels (`subFolder: error` and `subType: errors`) are excluded from both inbound and outbound UUFI paths. All client-visible request failures are reported on the originating resource channel via `schema/control_uufi.json`.

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
- **Single-Channel Query Service (Config, State, System Model):**
  - **Query:** Clients publish a `null`-payload request (`"payload": null` on MQTT; `"null"` or empty data on PubSub) with mandatory `principal` and `transactionId` directly on the target resource channel:
    - System Model: `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/model/system`
    - Cached State: `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/state/{subFolder}`
    - Active Config: `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/config/{subFolder}`
  - **Response:** The System replies on the **exact same channel** with `source: "udmis"`, copying the caller's `principal` and `transactionId`, returning either the matching domain payload (conforming to `<subType>_<subFolder>.json`), a lower-layer string sentinel if the target resource is absent, or a `schema/control_uufi.json` Control Ack on error. If no response is received within 15 seconds, the Client SHOULD consider the query timed out.
  - **Retirement of `subType: query`:** Channel-switching `query/*` topics (`query/system`, `query/state`) are retired on UUFI and MUST NOT be used.
- **Telemetry and Event Service:**
  - **Telemetry (Pointset):** Devices or clients publish pointset telemetry reports to `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/events/pointset`.
  - **Discovery:** Devices or clients publish discovery reports to `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/events/discovery`.
  - **Structure:** Event payloads MUST conform strictly to their respective authoritative UDMI schemas (`schema/events_pointset.json` or `schema/events_discovery.json`) nested within the standard `payload` key for MQTT or the root of the message data object for PubSub.
- **Control-Plane Progress & Error Service:**
  - **Same-Channel Control Replies:** The System reports request validation failures, processing errors (`status.level >= 500`), and interim progress updates (`status.level < 500`) directly on the originating resource channel using `schema/control_uufi.json` (root `"control"` block containing an `Entry` under `control.status`), copying `principal` and `transactionId` from the originating request.
  - **Retention Prohibition:** Transient UUFI `config/*` requests, Success Acks, and Control Acks MUST NOT be published with the MQTT `retain` flag set.

### 2.3. Resource Identifier Constraints
To ensure compatibility with MQTT topic parsing structures and PubSub attribute indexing, all identifier strings utilized in UUFI connection coordinates and paths MUST conform to the following character and length restrictions:
- **Allowed Characters:** Registry IDs (`{deviceRegistryId}`) and Device IDs (`{deviceId}`) MUST contain only uppercase and lowercase alphanumeric characters, hyphens (`-`), and underscores (`_`).
- **Length Limit:** Identifiers MUST be between 1 and 256 characters in length.
- **Prohibited Characters:** Spaces, slashes (`/`), backslashes (`\`), wildcards (`+`, `#`), and special punctuation characters are strictly prohibited.

## 3. Handshake Protocol

Handshake is **Client-initiated**. The **System MUST NOT initiate a handshake** unless acting as a Client.

The handshake has a 60-second timeout. On timeout, the Client should handle the failure and retry or fail-fast.

### 3.1. Logical Handshake Separation Layers

The UUFI interface separates network-level presence mapping from device-level operational control. Every component on the bus operates across two logical protocol layers:

1. **Layer 1: Service Handshake (Infrastructure-Level):**
   * Establishes communication presence and connection mapping on the bus between a Client and the Service.
   * Upon startup, every client MUST perform this handshake to verify connection integrity and register its active routing coordinates and prefix.
2. **Layer 2: Application Orchestration (Application-Level):**
   * Represents functional interactions (such as cloud model queries, telemetry publishing, or software updates).
   * These operations MUST only occur after the Layer 1 Service Handshaking of all participating components is complete.

### 3.2. Single-Responder Standard

To guarantee deterministic state transitions on shared handshake channels:
- **Only the central Service processes handshake requests on `/uufi/c/state/udmi` and publishes responses on `/uufi/c/config/udmi`.**
- Other application-layer services (such as coordinators, orchestrators like Butler, or compliance verifiers) MUST act strictly as handshake clients and **MUST NOT** publish handshake responses or act as responders on `/uufi/c/state/udmi`.

### 3.3. Service Handshake for Application-Level Clients

All active application-layer clients (such as orchestrators, parsers, or compliance verifiers) MUST execute their own Layer 1 Service Handshake upon initialization. This ensures that:
- The Service registers their connection.
- Other observers or verification engines can verify that the client is fully initialized, connected, and subscribed before sending subsequent updates.

### 3.4. Standard Handshake Flow and Payload Routing

For any client initializing on the bus, the handshake sequence MUST follow the **Request-Reply pattern** below:

```
 [ Client Entity ]                                                [ The Service ]
         │                                                            │
         │─── (1) Handshake Request (state/udmi) ────────────────────►│
         │    Topic: [/{prefix}]/uufi/c/state/udmi                    │
         │    Envelope:                                               │
         │      - transactionId: "UUID-A"                             │
         │      - principal: "{implementation_id}.{entity_suffix}"    │
         │    Payload:                                                │
         │      - setup: { "transaction_id": "UUID-A" }               │
         │                                                            │
         │◄── (2) Handshake Config Reply (config/udmi) ───────────────│
         │    Topic: [/{prefix}]/uufi/c/config/udmi                   │
         │    Envelope:                                               │
         │      - transactionId: "UUID-A" (Symmetric Match)           │
         │      - source: "udmis"                                     │
         │      - principal: "{implementation_id}.{entity_suffix}"    │
         │    Payload:                                                │
         │      - setup: { "deviceRegistryId": "testing" }            │
         │      - reply: { "transaction_id": "UUID-A" }               │
         ▼                                                            ▼
```

### 3.5. Symmetric Envelope Transaction ID Gating

To prevent packet collisions and support trace correlation across shared handshake channels:
1. **Symmetric Trace Propagation:** The Service MUST copy the `transactionId` value from the incoming request envelope directly into the `transactionId` field of the outgoing reply envelope.
2. **Strict Request Gating:** The client MUST discard any handshake replies whose envelope `transactionId` does not match its original request's `transactionId`.
3. **Trace-Level Tracking:** Clients and verifiers MUST track and propagate this `transactionId` across subsequent configurations and states to support end-to-end correlation.

### 3.6. Handshake Steps and Payload Standards

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
- **Addressing:** Envelope `principal` MUST match Client's identity (the static identity of the external application entity endpoints of the communication session). For handshake replies, the System MUST use the `principal` or `source` from the received state message to ensure the reply reaches the correct client. If the received message has a `principal`, it SHOULD be used; otherwise, the `source` SHOULD be used as a fallback. Senders MUST NOT set `principal` to `"uufi"` or a server instance ID. For broadcast messages with no specific receiver, `principal` MUST be left blank. The System MUST set `source` to `"udmis"` on outgoing messages for loopback prevention.

**Retries:** The Client SHOULD periodically republish the Step 1 state message (e.g., every 5 seconds) if a valid Step 2 confirmation has not been received, until the 60-second timeout.

**Activation:** The Client is **Active** when `reply.transaction_id` matches the original `state.setup.transaction_id`.

### 3.7. Registry ID Discovery
- **Default:** `default`
- **Discovery:** The System MAY provide a `deviceRegistryId` in the `config.udmi` handshake reply to the Client. To ensure interoperability, the `deviceRegistryId` SHOULD be placed within the `setup` block of the payload. The Client SHOULD use this `deviceRegistryId` for all subsequent registry-scoped topics. The System MUST NOT expect to discover its own `deviceRegistryId` from Client-initiated handshakes. To prevent collisions in multi-registry environments where device IDs may not be globally unique, the Client identity (`source` in envelope and `msg_source` in setup payload) SHOULD be a structured identifier in the format `{registry_id}/{device_id}` if the client already has knowledge of its designated registry. Otherwise, if the registry is unknown, the Client identity is the bare `{device_id}`, and the System will assign a default registry ID (e.g., `default`). (Note: Use `deviceRegistryId` camelCase exactly as specified; case-insensitive or snake_case matching is NOT guaranteed).

## 4. Message Encapsulation

All messages are wrapped in a UUFI Envelope.

### Mandatory Payload Fields
Inner JSON object `payload` (for Writes, Streams, Success Acks, and Control Acks) MUST include:
- `timestamp`: RFC 3339 (minimal precision).
- `version`: UDMI schema version.

Special non-object payload forms:
- `null` (MQTT `"payload": null`, PubSub `"null"` or empty data): signifies a single-channel **Query (Read)**.
- Bare JSON `String`: signifies a lower-layer **String Sentinel** value transported intact without object metadata injection.

### Transport Mapping

| Transport | Envelope Location | Payload Location |
| :--- | :--- | :--- |
| **PubSub** | Message Attributes | Message Data (JSON object, `"null"`, or JSON `String`) |
| **MQTT** | JSON Wrapper | Payload `payload` key (JSON object, `null`, or JSON `String`) |

#### MQTT Constraints
- **Envelope Consistency & Header Duplication Rule:** Senders MUST populate and duplicate all defined envelope header fields (`subType`, `subFolder`, and when applicable `deviceRegistryId`, `deviceId`) within the JSON envelope payload object across all transport mechanisms. The envelope structure MUST be identical across both MQTT and PubSub transports. For MQTT transport, omitting any of these mandatory envelope header fields from the JSON payload object, or providing envelope fields that conflict with the topic path coordinates, MUST be treated as a validation error, and the message MUST be rejected.
- **Mandatory Envelope Fields:**
  - **Outgoing Messages (System → Client):** Outgoing messages MUST populate standard envelope fields (`subType`, `subFolder`, `deviceRegistryId`, `deviceId`, `projectId`, `transactionId`, `publishTime`, `source: "udmis"`, and `payload`), setting `principal` to the caller's identity on Acks and omitting `principal` on broadcast Streams.
  - **Incoming Messages (Client → System):** All incoming client messages MUST contain mandatory envelope fields (`subType`, `subFolder`, `deviceRegistryId`, `deviceId`, `projectId`, `transactionId`, `publishTime`, `source`, and **`principal`**). Client messages omitting `principal` MUST be dropped and logged without reply. If required routing coordinates are omitted or mismatch the topic path, the System rejects the request with a `schema/control_uufi.json` Control Ack (provided `principal` is present).
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
  - **PubSub Attributes:** Envelope attributes MUST include `deviceRegistryId` and `deviceId` (along with `subFolder` set to `"system"` and `subType` set to `"model"`). Queries send a `null` payload on this same channel.
- **Payload Structure:**
  The inner message payload MUST follow the flat, unnested `schema/model_system.json` schema representing only the specified device.
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

| UDMI Operation | Envelope `subType` | Envelope `subFolder` | Direction | Payload & Role Note |
| :--- | :--- | :--- | :--- | :--- |
| Handshake State | `state` | `udmi` | Publish | Layer 1 flat `UdmiState` (Step 1) |
| Handshake Config | `config` | `udmi` | Receive | Layer 1 flat `UdmiConfig` (Step 2) |
| Config Write / Ack | `config` | *varies* | Publish / Receive | Domain object (or sentinel) Write; same-channel Success Ack or `control_uufi.json` Control Ack |
| Config Query / Reply | `config` | *varies* | Publish / Receive | `payload: null` Query; same-channel domain payload (or sentinel) Success Ack |
| State Stream | `state` | *varies* | Receive | Broadcast Stream (`principal: null`) |
| State Query / Reply | `state` | *varies* | Publish / Receive | `payload: null` Query; same-channel cached state (or sentinel) Success Ack |
| Telemetry Stream | `events` | `pointset` | Receive | Broadcast Stream (`principal: null`) |
| Discovery Stream | `events` | `discovery` | Receive | Broadcast Stream (`principal: null`) |
| System Model Query / Reply | `model` | `system` | Publish / Receive | `payload: null` Query; same-channel `model_system.json` (or sentinel) Success Ack |
| System Model Update / Ack | `model` | `system` | Publish / Receive | Partial-merge `model_system.json` Write; same-channel Success Ack |
| Blobset Config / Ack | `config` | `blobset` | Publish / Receive | `config_blobset.json` Write; same-channel Success Ack or Control Ack |
| Blobset State | `state` | `blobset` | Receive | Broadcast Stream (`principal: null`) |
| Control Progress / Error | *matches request* | *matches request* | Receive | `control_uufi.json` (`status.level < 500` progress, `>= 500` terminal error) |

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
- **Envelope Header Duplication Rule:** Implementations MUST duplicate all envelope header attributes (`subType`, `subFolder`, `deviceRegistryId`, `deviceId`) inside the JSON payload object wrapper. The JSON envelope structure MUST be identical across MQTT and PubSub transports. If an incoming MQTT message omits these envelope fields from the JSON payload or if they mismatch the topic path parameters, it MUST be rejected as an error.
- **Leading Slash:** For MQTT transport, all UUFI topics MUST start with a leading slash `/`. Implementations MUST NOT accept or publish to topics lacking the leading slash.

## 9. Test Setup for External Clients

This section specifies how external client developers (working outside the UDMI project codebase) can set up a local testing environment to build and verify custom UUFI Client implementations against a running System and Device Under Test (DUT).

### 9.1. Local System Infrastructure (`bin/udmi start`)
To initialize the local System stack (Mosquitto broker, etcd, and UDMIS control plane), execute the local startup script with a designated target site model directory and connection spec:

```bash
bin/udmi start [site_model_dir] //mqtt/localhost:$port
```

Example:
```bash
bin/udmi start sites/udmi_site_model //mqtt/localhost:46432
```

- **Format Constraint:** The target connection spec MUST be provided in the format `//mqtt/localhost:$port` to ensure correct port parsing and target identification by the local environment.
- **Shutdown & Lifecycle Provisions:** Service processes are managed directly via the tooling rather than manual process matching or `kill`/`pkill` commands. To stop the local system infrastructure, execute `bin/udmi stop`. Re-executing `bin/udmi start` automatically stops and cleans up any existing background instances before starting fresh.

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

- **Format Constraint:** The second positional argument MUST begin with `//` (e.g. `//mqtt/localhost:$port`). Omitting `//` or passing explicit schemes like `mqtts://` is not supported for local test execution and will result in a connection configuration error.
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
- **Expected Console Output:** When executed successfully, the utility confirms the atomic update of the target device's metadata file on disk and reports publishing the model update to the device-scoped system channel:
  ```text
  Successfully updated expected version of system to '1.0.0' in sites/udmi_site_model/devices/AHU-1/metadata.json.
  Publishing model/system update to /uufi/r/ZZ-TRI-FECTA/d/AHU-1/c/model/system...
  Model update message triggered successfully.
  ```
- **Expected Wire-Level Message Output:** The utility publishes a flat, device-scoped system model update message directly to the topic path `[/{prefix}]/uufi/r/{deviceRegistryId}/d/{deviceId}/c/model/system` (for example, `/uufi/r/ZZ-TRI-FECTA/d/AHU-1/c/model/system`). The published MQTT payload contains the standard envelope metadata and the flat `schema/model_system.json` structure:
  ```json
  {
    "projectId": "vibrant",
    "transactionId": "TXN-a1b2c3d4",
    "publishTime": "2026-07-24T06:57:00Z",
    "source": "site_trigger",
    "payload": {
      "version": "1.5.2",
      "timestamp": "2026-07-24T06:57:00Z",
      "software": {
        "system": "1.0.0"
      }
    }
  }
  ```

### 9.5. Hermetic Environment Teardown
To tear down or stop running test infrastructure and DUT simulations in isolated or non-privileged environments, use the provided high-level tool commands:

- **System Infrastructure Teardown:** Stop all local infrastructure services (such as the broker, etcd, influxd database, and registry plane) using the stop subcommand on the local startup service:
  ```bash
  bin/udmi stop
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

### 9.7. Utility Usage Restrictions
* **Permitted Utilities:** _Only_ utilities explicitly listed in the testing spec are allowed to be used by outside agents.
* **Prohibition:** The direct use of any other utility is prohibited and is not supported.

### 9.8. Environment Variable Restrictions
* **Prohibition:** For test compliance, it is forbidden to set any environment variable to try and influence or impact the UDMI implementation.

### 9.9. Agentic Test Infrastructure Management (MCP Server & CLI)
During development or for automated CI runners, the test infrastructure is managed via the **Test Infrastructure MCP Server** (`bin/test_infra_mcp`) or unified CLI (`bin/test_setup`). This abstracts away manual process management, PID tracking, and socket polling.

- **MCP Tools:**
  - `ensure_test_setup(test_id, [site_model], [dut_device_id], [dut_serial_no], [clean], [timeout_seconds])`: Deterministically assigns an isolated port block, launches the service stack in an isolated `tmux` session, verifies stack readiness, and returns the active connection URL (`mqtt://rocket:monkey@localhost:<port>`), available semantic window tags (`["main", "dut"]`), TLS certificates, and port mapping.
  - `terminate_test_setup(test_id, [clean_workspace])`: Destroys the `tmux` session and purges per-instance runtime storage.
  - `list_test_setups()`: Lists all active test sessions, active semantic windows, and connection endpoints.
  - `list_test_windows(test_id)`: Lists the available semantic window tags for an active test session.
  - `get_test_logs(test_id, [window], [lines])`: Captures live console output from a named semantic tmux window (e.g. `'main'`, `'dut'`). Window identifiers must be semantic tags; numerical indices are rejected.

- **CLI Usage:**
  ```bash
  # Ensure infrastructure is ready and obtain connection URL
  CONN_URL=$(bin/test_setup ensure my_test_id sites/udmi_site_model)

  # Full JSON response (includes "windows": ["main"])
  bin/test_setup ensure my_test_id sites/udmi_site_model --json

  # Query available semantic windows
  bin/test_setup windows my_test_id

  # Capture logs using semantic window tag
  bin/test_setup logs my_test_id main -n 50

  # Terminate infrastructure
  bin/test_setup terminate my_test_id
  ```

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
  "subType": "state",
  "subFolder": "udmi",
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
  "subType": "config",
  "subFolder": "udmi",
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
  "subType": "config",
  "subFolder": "pointset",
  "deviceRegistryId": "reg-1",
  "deviceId": "dev-1",
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
  "subType": "config",
  "subFolder": "blobset",
  "deviceRegistryId": "reg-1",
  "deviceId": "dev-1",
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
  "subType": "model",
  "subFolder": "system",
  "deviceRegistryId": "reg-1",
  "deviceId": "dev-1",
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

### A.1.9. Single-Channel Query (`payload: null`)

**PubSub Attributes** (Data is `"null"`):
```json
{
  "subType": "state",
  "subFolder": "pointset",
  "deviceRegistryId": "reg-1",
  "deviceId": "dev-1",
  "transactionId": "UUFI:sess123:005",
  "source": "client-id",
  "principal": "client-id@"
}
```

**MQTT Topic:** `/uufi/r/reg-1/d/dev-1/c/state/pointset`
```json
{
  "subType": "state",
  "subFolder": "pointset",
  "deviceRegistryId": "reg-1",
  "deviceId": "dev-1",
  "projectId": "vibrant",
  "transactionId": "UUFI:sess123:005",
  "publishTime": "2026-11-24T19:00:00Z",
  "source": "client-id",
  "principal": "client-id",
  "payload": null
}
```

### A.1.10. Control-Plane Progress & Error Replies (`schema/control_uufi.json`)

**PubSub Control Error Reply** (`projects/<proj>/topics/udmi_uufi`):

Attributes:
```json
{
  "subType": "config",
  "subFolder": "pointset",
  "deviceRegistryId": "reg-1",
  "deviceId": "dev-1",
  "transactionId": "UUFI:sess123:006",
  "source": "udmis",
  "principal": "client-id@",
  "publishTime": "2026-11-24T19:00:01Z"
}
```

Data:
```json
{
  "version": "1.5.7",
  "timestamp": "2026-11-24T19:00:01Z",
  "control": {
    "status": {
      "message": "Invalid pointset payload",
      "detail": "IllegalArgumentException: ...",
      "category": "system.config.parse",
      "level": 500,
      "timestamp": "2026-11-24T19:00:01Z"
    }
  }
}
```

**MQTT Control Progress Reply** (`/uufi/r/reg-1/d/dev-1/c/config/pointset`):
```json
{
  "subType": "config",
  "subFolder": "pointset",
  "deviceRegistryId": "reg-1",
  "deviceId": "dev-1",
  "projectId": "vibrant",
  "transactionId": "UUFI:sess123:007",
  "publishTime": "2026-11-24T19:00:01Z",
  "source": "udmis",
  "principal": "client-id",
  "payload": {
    "version": "1.5.7",
    "timestamp": "2026-11-24T19:00:01Z",
    "control": {
      "status": {
        "message": "Applying configuration (step 1/2)",
        "category": "system.config.receive",
        "level": 200,
        "timestamp": "2026-11-24T19:00:01Z"
      }
    }
  }
}
```

## A.2. Authoritative Schemas

UUFI implementations MUST adhere to the following schemas from the UDMI repository:

| UUFI Component | Authoritative UDMI Schema |
| :--- | :--- |
| **Message Envelope** | `schema/envelope.json` |
| **Control-Plane Reply** | `schema/control_uufi.json` |
| **Handshake State** | `schema/state_udmi.json` |
| **Handshake Config** | `schema/config_udmi.json` |
| **System Model** | `schema/model_system.json` |
| **Pointset Config** | `schema/config_pointset.json` |
| **Discovery Config** | `schema/config_discovery.json` |
| **Blobset Config** | `schema/config_blobset.json` |
| **Blobset State** | `schema/state_blobset.json` |
