THIS IS A PROVISIONAL SPEC THAT IS NOT NECESSARILY REPRESENTATIVE OF THE CURRENT IMPLEMENTATION.

# Unified UDMI Functional Interface (UUFI)

The **Unified UDMI Functional Interface (UUFI)** defines a standardized messaging mechanism for external applications (the **Client**) to integrate with a UDMI-managed system (the **System**).

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
- **Prefix Isolation:** The `prefix` MUST be used to isolate different environments sharing the same broker. If provided, it MUST be the leading part of the topic path (e.g. matching all segments of the path provided in the connection string). Implementations MUST support multi-segment prefixes and MUST NOT omit the prefix if provided in the connection string. All active subscriptions (including those for traffic observation) MUST be scoped to the provided prefix to ensure environmental isolation. Prefix enforcement MUST be strict: implementations MUST NOT publish to or subscribe from topics outside their designated prefix tree. To avoid collisions when multiple clients share the same broker, implementations MUST use unique MQTT Client IDs, for example by incorporating the prefix, a random nonce, or a combination of both.
- **Project Identity:** For the MQTT transport, the `projectId` field in the envelope SHOULD be treated as a general environment or project identifier. All components within a single UUFI session MUST use a consistent `projectId` (default: `vibrant`) to avoid ambiguity in message processing.
- **Cloud Model Service:**
  - **Discovery:** Clients publish a `query/cloud` message to `[/{prefix}]/uufi/c/query/cloud`.
  - **Response:** System publishes the model to `[/{prefix}]/uufi/c/config/cloud`.
  - **Structure:** Uses nested **Registries** (Section 5.1).

## 3. Handshake Protocol

Handshake is **Client-initiated**. The **System MUST NOT initiate a handshake** unless acting as a Client.

- **Wait for Handshake**: The System SHOULD wait for at least one Client handshake before becoming fully active, but it MUST NOT block indefinitely if no Clients are present.
- **Local Blobs**: For local file references, the `url` MUST use the `file://` scheme. Recipients MUST strip the scheme to resolve the path.
- **Metadata**: Device metadata (`make`, `model`) SHOULD be stored in a dedicated `meta` subsystem within the cloud model for consistency.

### Step 1: State Declaration
The Client publishes a UDMI `state` message to `/uufi/c/state/udmi`.
- **Payload:** Must include `udmi.setup` (see Appendix A.2).
- **Addressing:** Registry-less topic. `source` in envelope contains Client identity.

### Step 2: Configuration Confirmation
The System publishes a UDMI `config` message to `/uufi/c/config/udmi`.
- **Payload:** Must include `udmi.setup` and `udmi.reply` (see Appendix A.3).
- **Addressing:** Envelope `principal` MUST match Client's identity. For handshake replies, the System MUST use the `principal` or `source` from the received state message to ensure the reply reaches the correct client. If the received message has a `principal`, it SHOULD be used; otherwise, the `source` SHOULD be used as a fallback.

**Retries:** The Client SHOULD periodically republish the Step 1 state message (e.g., every 5 seconds) if a valid Step 2 confirmation has not been received, until the 60-second timeout.

**Activation:** The Client is **Active** when `udmi.reply.transaction_id` matches the original `state.udmi.setup.transaction_id`.

### Registry ID Discovery
- **Default:** `default`
- **Discovery:** The System MAY provide a `deviceRegistryId` in the `config.udmi` handshake reply to the Client. To ensure interoperability, the `deviceRegistryId` SHOULD be placed within the `udmi.setup` block of the payload. The Client SHOULD use this `deviceRegistryId` for all subsequent registry-scoped topics. The System MUST NOT expect to discover its own `deviceRegistryId` from Client-initiated handshakes. (Note: Use `deviceRegistryId` camelCase exactly as specified; case-insensitive or snake_case matching is NOT guaranteed).
- **Responsiveness:** MQTT message callback handlers MUST NOT perform long-running or blocking operations (e.g., `time.sleep()`). Any simulated work or heavy processing MUST be offloaded to a separate thread to maintain system-wide responsiveness and avoid buffer overflows or message loss in high-concurrency environments.

### 3.1 Interoperability Reminders
- **Metadata Persistence:** System components MUST ingest and cache `make` and `model` information from all available sources (registration, cloud updates, and state reports). Device simulators SHOULD include these fields in every state report to ensure consistency.
- **Update Config Keys:** Implementations MUST use `version` and `url` keys in the `update` subfolder config payloads. Avoid legacy keys like `target_version` or `bundle_url`.
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

## 5. Cloud Model Operations

### 5.1. Schema
- **Operation:** `READ`, `CREATE`, `UPDATE`, `DELETE`, `BIND`, `UNBIND`.
- **Registries:** Map of `{registry_id}` to a map of `{device_id}` to a map of `{subsystem_id}` to subsystem state.
- **Detail:** Optional parameters.

### 5.2. Update Semantics (Partial Merge)
The `UPDATE` operation for the `cloud` subfolder is a partial merge at the device subsystem level. Existing fields not in the payload MUST NOT be modified.

## 6. UDMI to UUFI Mapping

| UDMI Operation | Envelope `subType` | Envelope `subFolder` | Direction |
| :--- | :--- | :--- | :--- |
| Handshake State | `state` | `udmi` | Publish |
| Handshake Config | `config` | `udmi` | Receive |
| Config Update | `config` | *varies* | Publish |
| State Event | `state` | *varies* | Receive |
| Telemetry | `events` | `pointset` | Receive |
| Discovery | `events` | `discovery` | Receive |
| Model Query | `query` | `cloud` | Publish |
| Model Update | `model` | `cloud` | Publish |
| Model Reply | `config` | `cloud` | Receive |
| Update Config | `config` | `update` | Publish |
| Update State | `state` | `update` | Receive |

## 7. Reliability

### MQTT QoS
- **Requirement:** QoS 1 (At Least Once) for all state and configuration messages.

### Idempotency
- **Transaction ID:** MUST use a unique `transactionId` for message identification.
- **Deduplication:** Track `transactionId`s for 5 minutes.

## 8. Payload and Formatting Rules

### 8.1. Payload Structure
- **Nesting:** The `payload` object contains the fields of the UDMI message corresponding to the `subFolder` and `subType`.
- **Subsystem Nesting:** For `update` config and state payloads, data MUST be nested within a subsystem-id key (e.g., `system`) to support multi-subsystem devices. Implementations MUST handle both nested and unnested (flat) payloads for backward compatibility and robust interoperability.
- **Mandatory Fields:** `timestamp` and `version` MUST be at the root of the `payload` object.
- **Metadata:** The `make` and `model` fields are mandatory for all `update` subfolder payloads (state and config) within the subsystem nesting. These fields are essential for the System to locate the correct blob in the repository and MUST be included in every subsystem entry subject to reconciliation.
- **Update Config URL:** The `url` field in an `update` config payload MUST be a valid URI. Implementations MUST support the `file://` scheme for local file references. When a `file://` URI is provided, the recipient MUST strip the scheme and any leading slashes as appropriate for the local operating system to resolve the absolute or relative path.

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

# Appendix A: Schemas and Examples

This appendix contains the formal JSON schemas and message examples for the UUFI protocol.

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
  "udmi": {
    "setup": {
      "functions_ver": 9,
      "transaction_id": "UUFI:sess123:001",
      "msg_source": "client-id"
    }
  }
}
```

### A.1.2. Pointset Config (MQTT)

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

### A.1.3. Update Config (MQTT)

**Topic:** `/uufi/r/reg-1/d/dev-1/c/config/update`

**Payload:**
```json
{
  "transactionId": "UUFI:sess123:003",
  "principal": "client-id",
  "payload": {
    "version": "1.5.2",
    "timestamp": "2026-04-29T10:10:00Z",
    "update": {
      "system": {
        "version": "2.1.0",
        "url": "file:///path/to/bundle.bin",
        "sha256": "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
        "make": "Acme",
        "model": "Rocket-100"
      }
    }
  }
}
```

## A.2. UUFI Message Envelope Schema
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "UufiEnvelope",
  "type": "object",
  "properties": {
    "projectId": { "type": "string", "description": "GCP Project ID" },
    "deviceRegistryId": { "type": "string", "description": "Managed Registry ID (MUST NOT be used in MQTT)" },
    "deviceId": { "type": "string", "description": "Target/Source Device ID (MUST NOT be used in MQTT)" },
    "subFolder": { "type": "string", "description": "UDMI subFolder" },
    "subType": { "type": "string", "description": "UDMI subType" },
    "transactionId": { "type": "string", "description": "Tracking identifier" },
    "publishTime": { "type": "string", "format": "date-time", "description": "Envelope wrapping timestamp" },
    "source": { "type": "string", "description": "Client session identifier" },
    "principal": { "type": "string", "description": "Session owner identity" },
    "payload": {
      "type": "object",
      "description": "UDMI message container",
      "properties": {
        "timestamp": { "type": "string", "format": "date-time", "description": "UDMI message generation time" },
        "version": { "type": "string", "description": "UDMI schema version" }
      },
      "required": ["timestamp", "version"]
    }
  },
  "required": ["payload"]
}
```

## A.3. Handshake Schemas

### A.3.1. Handshake State Payload
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "HandshakeStatePayload",
  "type": "object",
  "properties": {
    "version": { "type": "string" },
    "timestamp": { "type": "string", "format": "date-time" },
    "udmi": {
      "type": "object",
      "properties": {
        "setup": {
          "type": "object",
          "properties": {
            "functions_ver": { "type": "integer", "description": "Expected UDMI functions version" },
            "transaction_id": { "type": "string", "description": "Handshake transaction ID" },
            "msg_source": { "type": "string", "description": "Originating client ID" },
            "user": { "type": "string", "description": "Authenticated user ID" }
          },
          "required": ["functions_ver", "transaction_id"]
        }
      },
      "required": ["setup"]
    }
  },
  "required": ["version", "timestamp", "udmi"]
}
```

### A.3.2. Handshake Config Payload
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "HandshakeConfigPayload",
  "type": "object",
  "properties": {
    "version": { "type": "string" },
    "timestamp": { "type": "string", "format": "date-time" },
    "udmi": {
      "type": "object",
      "properties": {
        "setup": {
          "type": "object",
          "properties": {
            "functions_min": { "type": "integer", "description": "Minimum supported functions version" },
            "functions_max": { "type": "integer", "description": "Maximum supported functions version" },
            "udmi_version": { "type": "string", "description": "System UDMI version" }
          }
        },
        "reply": {
          "type": "object",
          "properties": {
            "functions_ver": { "type": "integer", "description": "Reflected functions version" },
            "transaction_id": { "type": "string", "description": "Reflected transaction ID" },
            "msg_source": { "type": "string", "description": "Reflected client ID" }
          },
          "required": ["transaction_id"]
        }
      },
      "required": ["setup", "reply"]
    }
  },
  "required": ["version", "timestamp", "udmi"]
}
```

## A.4. Cloud Model Payload Schema
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "CloudModelPayload",
  "type": "object",
  "properties": {
    "version": { "type": "string" },
    "timestamp": { "type": "string", "format": "date-time" },
    "cloud": {
      "type": "object",
      "properties": {
        "operation": {
          "type": "string",
          "enum": ["READ", "CREATE", "UPDATE", "DELETE", "BIND", "UNBIND"],
          "description": "Model operation type"
        },
        "registries": {
          "type": "object",
          "description": "Map of registry_id to device configurations",
          "patternProperties": {
            "^[a-zA-Z0-9_-]+$": {
              "type": "object",
              "properties": {
                "devices": {
                  "type": "object",
                  "patternProperties": {
                    "^[a-zA-Z0-9_-]+$": {
                      "type": "object",
                      "description": "Map of subsystem_id to subsystem state",
                      "patternProperties": {
                        "^[a-zA-Z0-9_-]+$": {
                          "type": "object",
                          "description": "Device subsystem state",
                          "properties": {
                            "target_version": { "type": "string" },
                            "current_version": { "type": "string" },
                            "status": { "type": "string" },
                            "lkg_version": { "type": "string" },
                            "make": { "type": "string", "description": "Device manufacturer" },
                            "model": { "type": "string", "description": "Device model" }
                          }

                        }
                      }
                    }
                  }
                }
              }
            }
          }
        },
        "detail": { "type": "object", "description": "Operation-specific parameters" }
      },
      "required": ["operation", "registries"]
    }
  },
  "required": ["version", "timestamp", "cloud"]
}
```

## A.5. Update Config Payload Schema
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "UpdateConfigPayload",
  "type": "object",
  "properties": {
    "version": { "type": "string" },
    "timestamp": { "type": "string", "format": "date-time" },
    "update": {
      "type": "object",
      "patternProperties": {
        "^[a-zA-Z0-9_-]+$": {
          "type": "object",
          "properties": {
            "version": { "type": "string", "description": "Target version for the subsystem" },
            "url": { "type": "string", "description": "Location of the update blob" },
            "sha256": { "type": "string", "description": "Hex-encoded SHA-256 hash of the blob" },
            "make": { "type": "string" },
            "model": { "type": "string" }
          },
          "required": ["version", "url", "sha256", "make", "model"]
        }
      }
    }
  },
  "required": ["version", "timestamp", "update"]
}
```
