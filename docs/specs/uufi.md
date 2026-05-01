[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [UUFI](#)

# Unified UDMI Functional Interface (UUFI)

The **Unified UDMI Functional Interface (UUFI)** is a specification for external applications to integrate with a UDMI-managed system. It formalizes the communication channel between an external application (the **Client**) and the UDMI cloud infrastructure (the **System**) using a standardized messaging mechanism.

UUFI provides a "clean room" interface for programmatic control of UDMI operations, including device management, telemetry consumption, and command injection, all while adhering to the standard UDMI schemas. It supports both **GCP PubSub** and **MQTT** as transport layers.

## 1. Architecture Overview

UUFI utilizes a messaging transport where the Client interacts with the System via dedicated topics and subscriptions. This connection acts as a gateway for all UDMI messages.

*   **Managed Registry:** The actual IoT registry containing physical or virtual devices being managed.
*   **System Interface:** The set of topics provided by the UDMI infrastructure to handle UUFI traffic.

### Message Flow
- **Publish (into UDMI):** The Client publishes a UDMI message to the **UUFI** topic. The message is wrapped in a UUFI Envelope.
- **Receive (from UDMI):** The System delivers messages from managed devices to the Client via a **UUFI** reply channel. Messages are encapsulated in a UUFI Envelope.

## 2. Connectivity and Authentication

### 2.1. PubSub Transport
The Client must have access to the GCP project where the UDMI system is deployed.

*   **Project ID:** The GCP project ID.
*   **Publish Topic:** `udmi_uufi` (or a namespace-prefixed version like `prefix-udmi_uufi`).
*   **Receive Subscription:** A subscription to the `udmi_uufi` topic (e.g., `prefix-udmi_uufi-user_id`).
*   **Authentication:** Standard **GCP IAM**.

### 2.2. MQTT Transport (Local Mosquitto)
For local testing or on-premise deployments, a standard MQTT broker (like Mosquitto) can be used.

*   **Broker URL:** Typically `tcp://localhost:1883` or `ssl://localhost:8883`.
*   **Topic Prefix:** `/uufi/r/{registryId}/d/{deviceId}` where `registryId` is the Managed Registry.
*   **Authentication:** Username/Password or mTLS (certificate-based).

## 3. Handshake Protocol

Upon connection, the Client must perform a handshake to synchronize with the System.

1.  **State Declaration:** The Client publishes a UDMI `state` message to the UUFI topic. This message must include a `udmi` subfolder with a `setup` block (see `state_udmi.json`).
    -   `functions_ver`: The version of the UDMI functions the Client expects.
    -   `transaction_id`: A unique ID for the handshake transaction.
2.  **Configuration Confirmation:** The System responds via the reply channel by updating the Client's `config`. This message includes a `udmi` subfolder (see `config_udmi.json`) containing:
    -   `setup`: System version information (min/max supported function versions).
    -   `reply`: A copy of the Client's setup block to confirm receipt.

The Client is considered **Active** only after receiving a configuration reply where the `transaction_id` inside the `udmi.reply` block matches the `transaction_id` sent in the original `state` message.

### Handshake Addressing
Because the initial handshake is generic and occurs before the Client is associated with a specific registry or device, a distinct addressing scheme is used:

- **PubSub:** The `deviceRegistryId` and `deviceId` message attributes MUST be empty strings (`""`).
- **MQTT:** The topic MUST use the prefix `/uufi/c/{source}/` where `{source}` is the Client's unique identifier. The resulting topic structure is `/uufi/c/{source}/{subType}/{subFolder}`.

### Timeouts and Retries
Clients SHOULD implement a handshake timeout (default 30s). If no matching configuration reply is received within this window, the Client SHOULD retry the handshake with an exponential backoff, utilizing a new `transaction_id` for each attempt.

## 4. Message Encapsulation

All messages exchanged via UUFI are wrapped in a **UUFI Envelope**.

### Envelope Fields
The following fields are available in the envelope to provide context for the message. Their presence depends on the transport and specific operation (they are not globally mandatory):
- `projectId`: The project identifier.
- `deviceRegistryId`: The `registry_id` of the Managed Registry.
- `deviceId`: The target or source device ID in the Managed Registry (e.g., `BLD-1`, `_validator`).
- `subFolder`: The UDMI subfolder (e.g., `pointset`, `system`, `validation`).
- `subType`: The UDMI message type (e.g., `events`, `state`, `config`, `commands`).
- `transactionId`: A unique string used to track requests and responses.
- `publishTime`: RFC 3339 timestamp of when the message was wrapped.
- `source`: An identifier for the Client's session/context (distinct from the identity used in the UDMI payload).

### Transport Mapping

| Transport | Envelope Location | Payload Location |
| :--- | :--- | :--- |
| **PubSub** | Message Attributes | Message Data (JSON) |
| **MQTT** | Topic Structure & Payload | Payload `payload` field |

#### MQTT Topic Structure
For the MQTT transport, the envelope fields are encoded in the topic path:
`/uufi/r/{registryId}/d/{deviceId}/{subType}/{subFolder}`

#### MQTT Message Wrap
Since MQTT 3.1.1 does not support separate attributes, the envelope fields are included in the JSON payload alongside the actual UDMI message. **Crucially, the JSON payload MUST NOT include fields that are already encoded in the MQTT topic structure.**

```json
{
  "transactionId": "UUFI:sess123:002",
  "payload": {
    "points": {
      "room_temperature": { "set_value": 22.5 }
    }
  }
}
```

## 5. Operational Commands

UUFI supports direct operations on the Cloud Model by setting specific attributes.

### Cloud Model Schema
The `CloudModel` object used in these operations contains:
- `operation`: The action to perform (`READ`, `CREATE`, `UPDATE`, `DELETE`, `BIND`, `UNBIND`).
- `registryId`: (Optional) The target registry if different from the envelope.
- `deviceId`: (Optional) The target device if different from the envelope.
- `detail`: (Optional) Additional parameters specific to the operation.

### Cloud Model Queries
- Set `subFolder: cloud` and `subType: query`.
- **Payload:** A `CloudModel` object with `operation: READ`.

### Cloud Model Updates
- Set `subFolder: cloud` and `subType: model`.
- **Payload:** A `CloudModel` object specifying the `operation` (e.g., `CREATE`, `UPDATE`, `DELETE`, `BIND`, `UNBIND`).

## 6. Mapping UDMI to UUFI Envelopes

| UDMI Operation | Envelope `subType` | Envelope `subFolder` | Direction |
| :--- | :--- | :--- | :--- |
| Device Config Update | `config` | *varies* (e.g., `pointset`) | Publish |
| Device State Event | `state` | *varies* (e.g., `system`) | Receive |
| Device Telemetry | `events` | `pointset` | Receive |
| Device Discovery | `events` | `discovery` | Receive |
| Handshake State | `state` | `udmi` | Publish |
| Handshake Config | `config` | `udmi` | Receive |

## 7. Examples

The following examples demonstrate how to format PubSub messages for common UUFI operations, grouped by logical exchange.

### 7.1. Handshake Exchange
The handshake synchronizes the Client and the System upon connection.

#### Step 1: Publish Handshake State
The Client initiates the session using generic addressing.

**PubSub Attributes:**
```json
{
  "projectId": "my-gcp-project",
  "deviceRegistryId": "",
  "deviceId": "",
  "subFolder": "udmi",
  "subType": "state",
  "transactionId": "UUFI:sess123:001",
  "source": "-my-user-id"
}
```

**PubSub Data (JSON):**
```json
{
  "version": "1.5.2",
  "timestamp": "2026-04-29T10:00:00Z",
  "udmi": {
    "setup": {
      "functions_ver": 9,
      "transaction_id": "UUFI:sess123:001",
      "msg_source": "-my-user-id",
      "user": "my-user-id"
    }
  }
}
```

#### Step 2: Receive Handshake Config
The System confirms the session is active.

**PubSub Attributes:**
```json
{
  "projectId": "my-gcp-project",
  "deviceRegistryId": "",
  "deviceId": "",
  "subFolder": "udmi",
  "subType": "config",
  "transactionId": "UUFI:sess123:001"
}
```

**PubSub Data (JSON):**
```json
{
  "version": "1.5.2",
  "timestamp": "2026-04-29T10:00:01Z",
  "udmi": {
    "setup": {
      "functions_min": 9,
      "functions_max": 9,
      "udmi_version": "1.5.2"
    },
    "reply": {
      "functions_ver": 9,
      "transaction_id": "UUFI:sess123:001",
      "msg_source": "-my-user-id"
    }
  }
}
```


### 7.2. Pointset Exchange
Interaction with a device's points (e.g., sensors and setpoints).

#### Action: Publish Config Update
Updating the `room_temperature` setpoint for device `BLD-1`.

**PubSub Attributes:**
```json
{
  "projectId": "my-gcp-project",
  "deviceRegistryId": "my-managed-registry",
  "deviceId": "BLD-1",
  "subFolder": "pointset",
  "subType": "config",
  "transactionId": "UUFI:sess123:002",
  "source": "-my-user-id"
}
```

**PubSub Data (JSON):**
```json
{
  "version": "1.5.2",
  "timestamp": "2026-04-29T10:05:00Z",
  "points": {
    "room_temperature": {
      "set_value": 22.5
    }
  }
}
```

#### Action: Receive Telemetry Event
Receiving the current `room_temperature` reading from device `BLD-1`.

**PubSub Attributes:**
```json
{
  "projectId": "my-gcp-project",
  "deviceRegistryId": "my-managed-registry",
  "deviceId": "BLD-1",
  "subFolder": "pointset",
  "subType": "events",
  "publishTime": "2026-04-29T10:06:00Z"
}
```

**PubSub Data (JSON):**
```json
{
  "version": "1.5.2",
  "timestamp": "2026-04-29T10:06:00Z",
  "points": {
    "room_temperature": {
      "present_value": 22.1
    }
  }
}
```

### 7.3. MQTT Examples
The following examples demonstrate the same operations using the MQTT transport, following the rule that topic-encoded fields are omitted from the payload.

#### Example: Handshake State (Publish)
Using generic addressing for the initial handshake.

**Topic:** `/uufi/c/-my-user-id/state/udmi`

**Payload (JSON):**
```json
{
  "transactionId": "UUFI:sess123:001",
  "payload": {
    "version": "1.5.2",
    "timestamp": "2026-04-29T10:00:00Z",
    "udmi": {
      "setup": {
        "functions_ver": 9,
        "transaction_id": "UUFI:sess123:001",
        "msg_source": "-my-user-id",
        "user": "my-user-id"
      }
    }
  }
}
```

#### Example: Pointset Config (Publish)
Updating device `BLD-1`.

**Topic:** `/uufi/r/my-managed-registry/d/BLD-1/config/pointset`

**Payload (JSON):**
```json
{
  "transactionId": "UUFI:sess123:002",
  "payload": {
    "version": "1.5.2",
    "timestamp": "2026-04-29T10:05:00Z",
    "points": {
      "room_temperature": {
        "set_value": 22.5
      }
    }
  }
}
```

## 8. Reliability and Error Handling

### MQTT Quality of Service
To ensure reliable delivery of state and configuration messages, all MQTT communications SHOULD use **QoS 1** (At Least Once).

### Error Reporting
When the System encounters an error processing a UUFI message, it will respond via the reply channel using the `error` subFolder. The payload will include:
- `category`: A string describing the error type (e.g., `auth`, `validation`, `not_found`).
- `message`: A human-readable description of the error.
- `transactionId`: The ID of the message that caused the error (if available).
