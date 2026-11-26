[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Blob Updates](#)

# Blob Updates Specification

The _Blob Updates API_ defines a standard mechanism for delivering data blobs to a device via the UDMI configuration channel. This mechanism is commonly utilized for firmware updates, software module installations, security keys, or large configuration packages that are too large to fit directly into standard JSON configuration messages.

A device indicates its supported data blobs through discovery mechanisms, and the cloud controls the download, validation, and application of these blobs through the `blobset` block in the device configuration.

---

## Architecture & Data Models

The blob update mechanism splits operations into individual named blob targets within a `blobs` map under the `blobset` object.

### Config Structure

The [`blobset` block in configuration](../../schema/config_blobset.json) contains a `blobs` object where each key represents a unique blob identifier (e.g., `_iot_endpoint_config`, `manufacturer_proprietary_module`). Each blob configuration object specifies:

* **`phase`**: The desired management phase of the blob. Supported value:
  * `final`: The cloud expects the device to fully download, verify, and apply this blob version.
* **`url`**: The location from which the device can fetch the blob payload. This could be an external URL (HTTP/HTTPS), a cloud storage URI, or inline base64 data encoded via a data URI schema (e.g., `data:application/json;base64,...`).
* **`sha256`**: A 64-character lowercase hexadecimal string representing the expected SHA-256 cryptographic checksum of the unencoded blob payload. This ensures tamper protection and validation.
* **`generation`**: An RFC 3339 UTC timestamp indicating when this version of the blob was generated. This serves as a unique version identifier.

### State Structure

The [`blobset` block in state](../../schema/state_blobset.json) reflects the processed condition of each blob. For each blob target, the device must report:

* **`phase`**: The current operational phase or result. Supported values:
  * `apply`: (Optional/Intermediate) The device is actively processing or applying the blob payload.
  * `final`: The device has completed processing the blob (successfully or with terminal failure).
* **`generation`**: The generation timestamp of the configuration blob currently active or processed.
* **`status`**: A standard [Entry status object](../../schema/entry.json) providing the success or failure results of the last processing attempt, including error levels, categories, and descriptive messages.

---

## Update Sequence Flow

> [!IMPORTANT]
> **Observable Update Process**
> Devices are required to emit standard hierarchical log categories at each distinct operational milestone. This systematic telemetry creates a fully observable update pipeline, allowing cloud management platforms and automated testing frameworks to trace payload downloads, verification steps, and installation progress in real-time.

A standard successful blob update execution flows as follows:

```mermaid
%%{wrap}%%
sequenceDiagram
    autonumber
    participant C as Cloud / Core Service
    participant D as Device
    
    C->>D: CONFIG MESSAGE<br/>blobset.blobs.<id> = { phase: "final", url: "...", sha256: "...", generation: "T1" }
    D->>D: Emit log category: blobset.blob.receive (DEBUG)
    D->>D: Emit log category: blobset.blob.fetch (DEBUG)
    D->>D: Download & verify SHA-256 hash
    D->>D: Emit log category: blobset.blob.parse (DEBUG)
    D->>C: STATE MESSAGE<br/>blobset.blobs.<id> = { phase: "apply", generation: "T1" }
    D->>D: Emit log category: blobset.blob.apply (NOTICE)
    D->>C: STATE MESSAGE<br/>blobset.blobs.<id> = { phase: "final", generation: "T1", status: null }
```

### Idempotency & Optimization
Devices **must** check the `generation` or cryptographic `sha256` hash of a newly received blob configuration against the currently active blob. If they match, the device **must not** re-fetch or re-apply the blob, and no duplicate lifecycle log messages should be emitted.

---

## Error Handling & Log Categories

Granular observability relies on consistent telemetry. When tracking the lifecycle of blob updates or reporting processing failures, the device must use hierarchical log categories and status entries under the `blobset.blob` namespace.

| Category | Level | Description / Failure Scenarios |
| :--- | :--- | :--- |
| **`blobset.blob.update`** | `INFO` | Information: General category for processing a blob update. |
| **`blobset.blob.receive`** | `DEBUG` | Emitted when a new or updated blob configuration block is received. |
| **`blobset.blob.fetch`** | `DEBUG`/`ERROR` | Emitted when starting a network fetch or reading inline payload, or when fetching fails (oversize/unreachable URL). |
| **`blobset.blob.parse`** | `DEBUG`/`ERROR` | Emitted when beginning the verification/checksum check, or when verification/parsing fails (corrupt/invalid/incompatible). |
| **`blobset.blob.apply`** | `NOTICE`/`ERROR`| Emitted when applying or executing the update block, or when application fails (dependency/installer failure), or when a restart is required. |
| **`blobset.blob.abort`** | `NOTICE`| Information: The active download or update process was canceled by the cloud or aborted locally. |
| **`blobset.blob.rollback`** | `NOTICE`| Information: A problem was detected post-apply, and the device is rolling back to the previous version. |

---

## Message Examples

### 1. Config Triggering a Module Update
```json
{
  "version": "1.5.2",
  "timestamp": "2026-05-04T13:00:00.000Z",
  "blobset": {
    "blobs": {
      "system": {
        "phase": "final",
        "url": "https://storage.googleapis.com/udmi-device-blobs/system_v2.bin",
        "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "generation": "2026-05-04T12:30:00.000Z"
      }
    }
  }
}
```

### 2. State Reporting Success
```json
{
  "version": "1.5.2",
  "timestamp": "2026-05-04T13:00:15.000Z",
  "blobset": {
    "blobs": {
      "system": {
        "phase": "final",
        "generation": "2026-05-04T12:30:00.000Z"
      }
    }
  }
}
```

### 3. State Reporting a Corrupt/Failed Update
```json
{
  "version": "1.5.2",
  "timestamp": "2026-05-04T13:00:08.000Z",
  "blobset": {
    "blobs": {
      "system": {
        "phase": "final",
        "generation": "2026-05-04T12:30:00.000Z",
        "status": {
          "level": 500,
          "category": "blobset.blob.parse",
          "message": "Downloaded payload SHA-256 hash mismatch",
          "timestamp": "2026-05-04T13:00:07.000Z"
        }
      }
    }
  }
}
```

---

## Ephemeral Blobset Downloads (`download/blobset`)

When delivering sensitive credentials or rotated private keys (`_iot_endpoint_config`), embedding secret material inline in `config.blobset` (via `data:` URLs) is undesirable because `config` messages are retained by the MQTT broker and stored in operational databases. Relying on on-device key generation is also not standard across heterogeneous edge hardware with varying entropy quality.

Instead, the cloud controls credential/key rotation by specifying a `download://` URL (e.g., `download://_iot_endpoint_config`) in `config.blobset.blobs.<key>.url` and delivering the sensitive payload out-of-band over `{topic_prefix}/download/blobset` ([`schema/download_blobset.json`](../../schema/download_blobset.json)).

### Transport & Security Guarantees
* **QoS 0 & Non-Retained**: Both the publisher and device subscriber **must** use **MQTT QoS 0** with `retain = false` on `{topic_prefix}/download/blobset` so the broker never persists unacknowledged secret payloads to durable session disk.
* **Integrity & Expiry**: Each entry in `download/blobset` (`blobs.<key>`) conforms to [`EndpointConfiguration`](../../schema/configuration_endpoint.json), supporting `expiry` (`date-time`), `auth_provider` (`basic`/`jwt`), and ephemeral `key_bytes` (PEM private key). The device verifies the canonical JSON SHA-256 digest against `config.blobset.blobs.<key>.sha256` and rejects expired payloads before applying.

### Handshake Flow
1. **Config Notification**: Cloud publishes `config.blobset.blobs._iot_endpoint_config` with `phase: "final"`, `generation`, `sha256`, and `url: "download://_iot_endpoint_config"`.
2. **Download Request (`phase: "apply"`)**: Seeing a new `generation` with a `download://` URL, the device publishes `state.blobset.blobs._iot_endpoint_config.phase = "apply"` with the matching `generation`.
3. **Ephemeral Delivery**: Cloud publishes a [`BlobsetDownload`](../../schema/download_blobset.json) message over QoS 0 non-retained `download/blobset` containing `blobs._iot_endpoint_config`.
4. **Verification, Application & Ack (`phase: "final"`)**: The device verifies `sha256` and `expiry`, atomically rotates the private key (if `key_bytes` is provided, backing up the previous key and stripping `key_bytes` from persisted endpoint JSON), acknowledges `phase: "final"`, and reconnects.

