## Technical Specification: Spotter (Python UDMI Reference Client)

**Spotter** is an extensible, Python-based reference client designed as a fully UDMI-compliant IoT device intended for on-premise deployment. While it serves as a robust test target for the Sequencer CI framework to validate OTA (Over-The-Air) update orchestration, its architecture is modular and production-ready, making it capable of handling real-world deployments and complex update scenarios natively.

---

### 1. Core Objective

To implement an atomic, configuration-driven execution handler that processes modular component updates (targeting `system.software.<client_defined_key>`) using the UDMI `blobset` protocol, while supporting extensible device capabilities (such as JWT authentication, telemetry logging, and dynamic dependency validation).

---

### 2. Functional Requirements

#### A. Atomic State Machine Implementation

Spotter inherently transitions through the standardized UDMI update phases:

* **Idle / Steady State**: Reports currently running module versions in `system.software`.
* **Apply Phase**: Upon receiving a valid `blobset` config, Spotter acknowledges by updating its state to `phase: apply` and initiating an out-of-band download.
* **Final Phase**: Reports `phase: final` upon successful application or fatal failure.

#### B. Payload Delivery & Validation

Spotter leverages the robust underlying UDMI Python library for reliable payload delivery:

* **Out-of-Band Download**: Retrieves payloads via HTTP(S) using URLs provided in the configuration.
* **Resumable Downloads**: Supports standard HTTP(S) `Range` requests to handle constrained network drops, falling back gracefully if the server ignores the Range header.
* **Cryptographic Verification**: Securely calculates the local SHA256 hash of the downloaded payload and verifies it against the mandatory 64-character hash in the cloud configuration before execution.

#### C. Git-Based Update Strategy (On-Premise Ready)

Spotter utilizes a real-world, Git-based update strategy for self-updating on-premise:

* The cloud payload provides a target Git commit hash.
* Before applying the update, Spotter fetches the remote repository and extracts a manifest (`spotter_manifest.json`) directly from the target commit using `git show`.
* It cross-references hardware requirements and dependencies from the target manifest against its current local state.
* Updates are applied natively by executing a `git checkout <hash>` and safely restarting the service via OS-level signals (e.g., `sys.exit(0)` for `systemd` recovery).

---

### 3. Error Taxonomy & Handling

Spotter strictly categorizes errors at both the network and application layers to prevent "bricking" or infinite retry loops.

| Error Type | Scenarios | Required Action |
| :--- | :--- | :--- |
| **Retryable** | Transient network drops, HTTP 503 | Handled natively by the UDMI fetcher via local exponential backoff and retry. |
| **Fatal (Auth/Net)** | Expired Signed URL, HTTP 401/403/404 | Abort installation immediately and report level 500 `ERROR`. |
| **Fatal (Integrity)** | SHA256 Hash Mismatch  | UDMI library securely discards the file, aborts, and reports a level 500 `ERROR`. |
| **Fatal (Logic)** | Hardware mismatch, missing manifest, or dependency conflict | Reject payload before `git checkout`, abort execution, and report level 500 `ERROR`. |

---

### 4. Telemetry & Observability

Spotter provides robust closed-loop visibility by publishing system milestones to the `events/system` MQTT pipeline:

* **Standardized Logs**: Directly logs `blobset.download.start`, `blobset.hash.verify`, and `blobset.apply.success` during the update lifecycle.
* **Decoupled Reporting**: Automatically attaches the `UDMIMqttLogHandler` to the root device logger. If an HTTP download or `git` operation fails, the resulting OS-level error logs are seamlessly routed through the primary MQTT telemetry channel as `SystemEvent` metrics, ensuring the cloud orchestrator is notified independently of the standard `state` update.

---

### 5. Compliance Checklist for Sequencer CI

Spotter guarantees compliance with the Sequencer CI framework by passing these six automated scenarios:

1. **Happy Path**: Successful download, hash match, dependency validation, and Git version update.
2. **Hash Mismatch**: Detection of corrupted SHA256 and secure file deletion.
3. **Invalid URL**: Handling of 403/404 errors without attempting installation or application logic.
4. **Hardware Mismatch**: Rejection of incorrect bundles (e.g., wrong controller type mapped against the fetched Git manifest).
5. **Corrupted Payload**: Trapping OS-level execution exceptions for malformed binaries or missing manifest files within the target Git commit.
6. **Dependency Mismatch**: Validating that new modules described in the remote target manifest are strictly compatible with existing local dependencies.
