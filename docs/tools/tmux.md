[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [tmux](#)

# UDMI Tmux Controllers & Namespaced Environments

UDMI provides modular `tmux`-encapsulated controllers for managing local service stacks, background daemons, and test harnesses with full environment isolation, diagnostic probes, and zero-sudo unprivileged execution.

---

## 1. Controller Architecture

The local infrastructure is divided into modular, decoupled service domains:

| Controller Script | Tmux Session | Default Services | Optional Services (`++svc`) | Description |
| :--- | :--- | :--- | :--- | :--- |
| **`bin/tmux_base`** | `udmi_base[~ns]` | `clone`, `certs` | *(none)* | Site model provisioning and CA / reflector certificate generation. |
| **`bin/tmux_barbican`** | `udmi_barbican[~ns]` | `etcd`, `mosquitto`, `udmis` | `pubsub` | Core message bus, distributed registry, and UDMIS control plane. |
| **`bin/tmux_butler`** | `udmi_butler[~ns]` | `postgres`, `influxdb`, `butler` | `validator`, `registrar` | Data persistence, telemetry storage, and event processing. |
| **`bin/tmux_bridgehead`** | `udmi_bridgehead[~ns]` | `pubber` | `spotter` | Device-under-test (DUT) and network discovery agent emulation. |
| **`bin/tmux_mcp`** | `udmi_mcp[~ns]` | `server` | *(none)* | FastMCP test and diagnostic automation server. |

---

## 2. Command Syntax

```bash
bin/tmux_<box> [command] [target_or_namespace] [project_spec] [target_id] [service_filters...]
```

Running any `bin/tmux_<name>` script with **no arguments** (or `help`, `-h`, `--help`) displays usage instructions and available services, then exits cleanly.

### Available Commands

The lifecycle of each controller revolves around four operational states:

* **`start`**: Initializes and starts configured services in a background `tmux` session. Preserves existing certificates, cloned models, and runtime database state (non-destructive, idempotent).
* **`stop`**: Shuts down services and terminates the `tmux` session **without** deleting site models, certs, or data files, preserving state for diagnostic inspection.
* **`clean`**: The destructive operation that purges runtime state, cached certificates, databases, and cloned site models (`sites/udmi~<ns>`) without starting any services.
* **`restart`**: Clean-slate fresh restart equivalent to the sequence `{ stop, clean, start }`.

#### Diagnostic & Inspection Commands:
* **`status`**: Displays session lifecycle status and semantic diagnostic health probes (PIDs, listening ports, health checks).
* **`logs`**: Dumps pane scrollback buffer (`bin/tmux_<box> logs [window] [lines]`).
* **`attach`**: Interactively attaches to the session or specific window (`Ctrl-b d` to detach).
* **`help`**: Shows available commands and service options.

---

## 3. Namespacing & Zero-Sudo Execution

### A. Semantic Namespace Identifiers (Recommended)
Passing a bare semantic namespace identifier (e.g. `btesting`, `alpha`, `staging`):
```bash
bin/tmux_barbican start btesting
```
- **Deterministic Port Mapping**: Derives an unprivileged port block using SHA-256 in the range `[20000, 55000]` ($\text{port} = 20000 + ((\text{SHA-256}(ns) \bmod 3500) \times 10)$).
- **Zero-Sudo**: Automatically sets `UDMI_NO_SUDO=true`, isolating data directories to `var/` and running user-space processes without root privileges.
- **Namespaced Sessions**: Sets session name to `udmi_<box>~<namespace>` (e.g. `udmi_barbican~btesting`).
- **Separable Site Model**: Resolves site model path to `sites/udmi~<namespace>`.

### B. Default Execution (No Target Provided)
When no target is passed (e.g. `bin/tmux_barbican start` or `bin/start_local`):
- Defaults to namespace **`default`**.
- Session name: `udmi_<box>~default`.
- Project spec: `//mqtt/localhost:35950/default`.
- Site model: `sites/udmi~default`.

### C. Explicit Site Model Path
When specifying a custom site model directory (e.g. `sites/my_site`):
- **Fail-Fast Rule**: An explicit site model path **requires** an explicit project specification:
  ```bash
  # Valid:
  bin/tmux_barbican start sites/my_site //mqtt/localhost:46432
  bin/tmux_barbican start sites/my_site btesting

  # Invalid (fails fast):
  bin/tmux_barbican start sites/my_site
  ```

---

## 4. Service Inclusion & Exclusion Filters

Fine-tune which services run inside the tmux session during `start`:

* **Inclusion (`+service`)**: Run *only* the specified service.
  ```bash
  bin/tmux_barbican start btesting +mosquitto
  ```
* **Exclusion (`!service`)**: Exclude a default service.
  ```bash
  bin/tmux_barbican start btesting !udmis
  ```
* **Optional (`++service`)**: Enable an optional service that is disabled by default.
  ```bash
  bin/tmux_barbican start btesting ++pubsub
  ```

---

## 5. Orchestration: `bin/start_local`

[`bin/start_local`](../../bin/start_local) unifies and manages the entire local service pipeline by delegating to `tmux_barbican` and `tmux_butler`:

```bash
# Start full local infrastructure under namespace 'btesting' (preserves state)
bin/start_local btesting

# Reset and start fresh under namespace 'btesting' (clean-slate)
bin/start_local restart btesting

# Start full stack under default namespace
bin/start_local

# Stop local infrastructure
bin/start_local stop btesting
bin/start_local stop
```
