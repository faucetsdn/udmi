[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [udmi](#)

# UDMI Local Orchestrator (`bin/udmi`)

`bin/udmi` is the singular, canonical orchestrator for managing local UDMI infrastructure services, background daemons, and test environments. It coordinates lower-level controllers ([`bin/tmux_barbican`](tmux.md) and [`bin/tmux_butler`](tmux.md)) to provide automated service lifecycle management with unprivileged, non-root execution.

---

## 1. Command Syntax

```bash
bin/udmi <command> [site_model] [project_spec] [target_id] [block] [+only | !exclude | ++optional]
```

### Commands

| Command | Description | State Behavior |
| :--- | :--- | :--- |
| **`start`** | Starts local UDMI infrastructure services in background tmux sessions. | Non-destructive; preserves existing database, certificates, and state. |
| **`stop`** | Gracefully terminates local services across tmux sessions. | Non-destructive; leaves runtime artifacts intact for post-mortem analysis. |
| **`clean`** | Purges runtime state, cached certificates, databases, and logs. | Destructive; removes `var/` runtime state without starting services. |
| **`restart`** | Clean-slate sequence: `{ stop, clean, start }`. | Destructive reset followed by fresh startup. |
| **`status`** | Displays diagnostic probes (PIDs, listening ports, health endpoints) for all services. | Read-only. |
| **`logs`** | Dumps pane scrollback logs (`bin/udmi logs [session[:window]] [num_lines]`). | Read-only. |
| **`attach`** | Attaches interactively to a session (`bin/udmi attach [session]`). | Interactive terminal (`Ctrl-b d` to detach). |
| **`help`** | Shows CLI usage and available options. | Read-only. |

---

## 2. Target Invocation Patterns

`bin/udmi` supports four deterministic, mutually exclusive target argument formats:

### A. Solo Managed Namespace (Recommended for Multi-Tenancy)
```bash
bin/udmi start btesting
bin/udmi start alpha
bin/udmi start  # Defaults to namespace 'default'
```
* **Namespace Resolution**: Sets namespace to `<ns>` (e.g. `btesting`, `alpha`, or `default`).
* **Deterministic Port Allocation**: Port block derived via SHA-256 in user-space range `[20000, 55000]` ($\text{port} = 20000 + ((\text{SHA-256}(ns) \bmod 3500) \times 10)$).
* **Isolated Site Model**: Uses or provisions `sites/udmi~<ns>`.
* **Project Spec**: Generates `//mqtt/localhost:<port>`.

### B. Solo Connection Spec
```bash
bin/udmi start //mqtt/localhost:46432
```
* **Target Resolution**: Uses exact port (e.g. `46432`) and targets the default site model `sites/udmi_site_model`.
* **No Namespace**: Does not create a namespaced session or prefix.

### C. Solo Configuration File
```bash
bin/udmi start path/to/cloud_iot_config.json
```
* **Target Resolution**: Uses the specified configuration file directly, deriving the site model from its parent directory.

### D. Explicit Model + Spec Pair
```bash
bin/udmi start sites/udmi_site_model //mqtt/localhost:46432
```
* **Target Resolution**: First argument must be an existing site model directory; second argument must be an explicit connection spec.

### Fail-Fast Invariants (Rejected Combinations)
* **Explicit Directory + Bare Namespace**: `bin/udmi start sites/udmi_site_model alpha` -> **REJECTED**. Mixing an explicit site model directory with a namespace is invalid. Use an explicit connection spec (e.g., `//mqtt/localhost:46432`) or a solo namespace.
* **Explicit Directory without Connection Spec**: `bin/udmi start sites/udmi_site_model` -> **REJECTED**. An explicit site model directory requires an explicit connection spec.
* **Invalid Target**: Any target that is not an existing directory, file, connection spec (`//...` or `:...`), or valid alphanumeric namespace identifier fails immediately with an error.

---

## 3. Non-Root Execution by Default

* **Default Non-Root Execution**: All scripts run in user-space by default without requiring `sudo` or root privileges. Runtime state, databases, and logs are isolated inside `var/` and `out/`.
* **Dynamic Root Detection**: Administrative operations (such as installing system packages via `apt-get` in [`bin/setup_base`](setup.md)) dynamically check `$(id -u) == 0`. Running `sudo bin/setup_base` installs system-level dependencies, while executing `bin/setup_base` unprivileged configures virtual environments and site models without triggering `sudo`.

---

## 4. Managed Services & Service Filters

`bin/udmi` coordinates two primary service controllers:
* **Barbican**: Core message bus and control plane (`mosquitto`, `udmis`, `etcd`, `pubsub`).
* **Butler**: Persistence and telemetry services (`butler`, `validator`, `registrar`, `postgres`, `influxdb`).

### Service Filter Modifiers

| Filter Syntax | Description | Example |
| :--- | :--- | :--- |
| **`+service`** | Run **only** the specified service(s). | `bin/udmi start btesting +mosquitto` |
| **`!service`** | **Exclude** the specified service from startup. | `bin/udmi start btesting !udmis` |
| **`++service`** | Enable an **optional** service (disabled by default). | `bin/udmi start btesting ++pubsub` |

---

## 5. Blocking Mode

When launching in automated environments or container entrypoints, append `block` to keep the process running in the foreground until interrupted:

```bash
bin/udmi start //mqtt/localhost:46432 block
```
