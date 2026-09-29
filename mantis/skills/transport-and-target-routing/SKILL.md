---
name: transport-and-target-routing
description: Navigational playbook for investigating UDMI project target specifications, transport routes (//gbos/, //gref/, //mqtt/), and reflector registries.
---

# Transport & Target Specification Navigational Playbook

This playbook guides the agent on where and how to investigate UDMI target specifications, connection transports, and reflector mechanisms in the codebase.

---

## 1. Target Specification Syntaxes & Parsing

UDMI tools (`sequencer`, `validator`, `mantis`, `udmis`, `registrar`, `pubber`) dynamically configure target IoT environments, provider semantics, and namespace isolation using project target specifications (`project_spec` / `target_spec`).

### Authoritative Implementations
- **Java**: [`SiteModel.java`](../../../common/src/main/java/com/google/udmi/util/SiteModel.java) (`SPEC_PATTERN`, `extractSpec`, `augmentConfig`).
- **Java Services**: [`AbstractPollingService.java`](../../../validator/src/main/java/com/google/udmi/util/AbstractPollingService.java) (`ProjectSpec` record).
- **Bash**: [`etc/shell_common.sh`](../../../etc/shell_common.sh) (`normalize_conn_spec`), [`bin/sequencer`](../../../bin/sequencer) (lines 128–153), [`bin/pubber`](../../../bin/pubber) (lines 104–135).
- **Python**: [`mantis/project_spec.py`](../../../mantis/project_spec.py) (`parse_project_spec`, `normalize_project_spec`, `resolve_target_spec`).
- **Workbench**: [`workbench/server/discovery.py`](../../../workbench/server/discovery.py) (`build_project_spec`, the spec suggested for a site model), [`workbench/server/testbed.py`](../../../workbench/server/testbed.py) (`LOCAL_SPEC_PATTERN`, the only spec the local testbed accepts).
- **Documentation**: [`docs/tools/project_spec.md`](../../../docs/tools/project_spec.md).

### The Canonical Specification Expression
Defined in `SiteModel.java` as:
```regex
(//([a-z]+)/)?(([a-z0-9:@.-]+))(/([a-z0-9]+))?(\+([a-z0-9-]+))?
```
Structural representation:
```
[//provider/]project[@bridge_host][/namespace][+user]
```

### Capture Groups & Component Semantics
1. **Provider (`//provider/`, Group 2, Optional)**:
   - Evaluated to `IotAccess.IotProvider`: `gbos`, `gref`, `mqtt`, `pubsub`, `jwt`, `clearblade`, `local`, `dynamic`, `implicit`, `etcd`.
   - If omitted:
     - Strings with `bos-platform-` or cloud patterns default to `gbos`.
     - Strings with `:` (e.g. `localhost:18833`) or standalone words default to `mqtt`.
2. **Project & Endpoint (`project`, Group 4, Required)**:
   - Supports GCP project IDs (`bos-platform-staging`, `bos-platform-dev`), hostnames (`localhost`, `127.0.0.1`, `mosquitto`).
   - Special Value `--` or `_`: Mapped to `NO_SITE` (null `project_id`), indicating disconnected or local execution.
   - **Port Override (`:port`)**: e.g. `//mqtt/localhost:46432`. Sets `project_id="localhost"`, `bridge_host="localhost:46432"`.
   - **Bridge Host Override (`@bridge_host`)**: e.g. `//gbos/bos-platform-dev@mqtt.bos.goog`, `//jwt/bos-platform-dev@mqtt.bos.goog`, `//mqtt/_@localhost:18833`. Sets `project_id` to prefix before `@` (or null if `_`), and `bridge_host` to host after `@`.
3. **Namespace (`/namespace`, Group 6, Optional)**:
   - Multi-tenant isolation qualifier (`[a-z0-9]+`, e.g. `/faucetsdn`, `/udmis`).
   - Prefixes cloud resources with delimiter `~` (e.g. `<namespace>~UDMI-REFLECT`, `<namespace>~udmi_reflect`).
4. **User (`+user`, Group 8, Optional)**:
   - Concurrent execution domain (`[a-z0-9-]+`, e.g. `+dev_user`, `+runner1`).
   - **Rule**: Supported for `gref` and `pubsub`. Specifying `+user` on `gbos` or `mqtt` is rejected with an error by Java tools (`user name not supported`).

### Exhaustive Syntax Variants
| Syntax Variant | Example | Interpreted Semantics |
| :--- | :--- | :--- |
| **Canonical Cloud Reflector** | `//gbos/bos-platform-dev/faucetsdn` | ClearBlade/IoT Core reflector targeting registry `faucetsdn~UDMI-REFLECT` |
| **Direct Pub/Sub Reflector** | `//gref/bos-platform-staging+dev_user` | GCP PubSub reflector on topic `udmi_reflect`, reply subscription `udmi_reply+dev_user` |
| **Direct Local Broker** | `//mqtt/localhost:46432` | Local isolated Mosquitto broker on port 46432 |
| **Bridge Host Override** | `//gbos/bos-platform-dev@mqtt.bos.goog` | GBOS project `bos-platform-dev` connecting via bridge `mqtt.bos.goog` |
| **URL Scheme Formats** | `mqtt://localhost:18833/faucetsdn` | Converted by `normalize_conn_spec` to `//mqtt/localhost:18833/faucetsdn` |
| **Shorthand Provider** | `gbos/bos-platform-staging` | Normalized to `//gbos/bos-platform-staging` |
| **Shorthand Cloud Project** | `bos-platform-staging` | Recognized by prefix `bos-platform-` -> `//gbos/bos-platform-staging` |
| **Semantic Local Namespace** | `btesting`, `default` | Hashed by `derive_port_from_namespace` to unprivileged port (e.g. `35300`) -> `//mqtt/localhost:35300/btesting` |
| **Mock / Offline Mode** | `--`, `mock-project`, `mock-clean` | Configures in-memory mock testing without network/cloud access |
| **Disconnected Local Mode** | `//mqtt/_@localhost:18833` | Null `project_id`, local bridge host `localhost:18833` |

---

## 2. General Transport Diagnostic Methodology

When diagnosing transport timeouts, connection errors, or communication failures:

### Communication Path Boundaries
- Parse the target specification using `parse_project_spec` or inspect the provider syntax (`//gbos/`, `//gref/`, `//mqtt/`, `//pubsub/`).
- Determine the transport boundaries:
  * **Direct Local Broker** (`//mqtt/...`): Client connects directly to local MQTT broker (Mosquitto). Verify broker process health, port availability, and credentials.
  * **Cloud Reflector** (`//gbos/...`): Client connects via cloud bridge (e.g., ClearBlade / IoT Core) through reflector registries (`<namespace>~UDMI-REFLECT`).
  * **Direct Cloud Pub/Sub** (`//gref/...` or `//pubsub/...`): Client interacts directly with GCP Pub/Sub topics and subscriptions (`udmi_reflect`, `udmi_reply`).
