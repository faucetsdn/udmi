[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Project Spec](#)

# Project Specification

The Project Specification (`project_spec`) is a string format used by UDMI tools, primarily the `registrar` and `validator`, to dynamically configure the target IoT environment, provider semantics, and namespace isolation.

## Format Specification

The string is evaluated according to the following regular expression:

```regex
^(//([a-z]+)/)?([a-z-]+)(/([a-z0-9]+))?(\+([a-z0-9-]+))?$
```

*Note: The actual implementation in Java uses `(//([a-z]+)/)?(([a-z-]+))(/([a-z0-9]+))?(\\+([a-z0-9-]+))?` with specific capture groups.*

The structure can be conceptualized as:
`[//provider/]project[/namespace][+user]`

### Component Breakdown

#### `provider` (Group 2, Optional)
The provider designates the primary IoT backend or protocol used by the tool.
* **Optionality**: The code parses this as optional (`(//([a-z]+)/)?`). If omitted, the `ExecutionConfiguration`'s `iot_provider` will not be overridden by the project spec, although tools may assume a default based on external configurations.
* **Supported Values**:
  * `gbos`: Uses reflector client through IoT Core.
  * `gref`: Uses reflector client through GCP PubSub.
  * `mqtt`: Uses reflector client through an MQTT broker.
  * `pubsub`: Uses direct access through PubSub (primarily for `validator`).
  * `clearblade`: Connects to ClearBlade IoT Core.
  * *Other valid schema values*: `local`, `dynamic`, `implicit`, `etcd`, `jwt`.

#### `project` (Group 3/4, Required)
The project identifier is the core mandatory component of the spec. Its specific meaning depends heavily on the chosen `provider`.
* **Semantics by Provider**:
  * `gbos`, `clearblade`: IoT Core project ID.
  * `gref`, `pubsub`: GCP project ID.
  * `mqtt`: Broker hostname (e.g., `localhost`).
* **Special Case (`no-site`)**: The exact string `no-site` is mapped to a null `project_id` internally, signaling the tool to operate entirely locally or disconnected from a remote project.

#### `namespace` (Group 6, Optional)
The namespace allows for multiple parallel instances within the same project.
* **Usage**: When specified, it is automatically prefixed to relevant resources (e.g., PubSub topics, registry IDs) using a designated delimiter (often `~`).
* **Default**: Defaults to an _empty_ prefix (i.e., no namespacing).

#### `user` (Group 8, Optional)
The user component suffix designates concurrent execution domains for different users on the same project without colliding on subscriptions or sessions.
* **Supported vs. Unsupported**:
  * `gref`, `pubsub`: Supported. If not explicitly specified, defaults to `debug`.
  * `gbos`, `mqtt`: **Not supported.** Specifying a user here will result in a runtime error or undefined behavior as only a single client connection is logically mapped.

---

## Tooling Context: `registrar`

The `registrar` tool uses the `project_spec` to determine how to connect to the target environment to register devices, update metadata, and map physical topology into cloud constructs.
- It evaluates the spec to populate the `ExecutionConfiguration`, overriding fields like `iot_provider`, `project_id`, `udmi_namespace`, and `user_name`.
- When instantiating `CloudIotManager` or `PubSubPusher`, the extracted project and namespace guide topic creation and registry API targeting.

## Implementation Deviations

While this document aims to be an authoritative specification, developers should be aware of the following discrepancies between documentation and current code implementation:

1. **Provider Optionality**: Documentation historically implied `//provider/project` was mandatory. The regex `(//([a-z]+)/)?` allows dropping the provider (e.g., simply `my-project`). In such cases, the provider may default or fail depending on tool-specific initialization logic.
2. **Additional Providers**: The tools accept a broader list of providers internally via the `IotProvider` enum (like `clearblade`, `local`, `dynamic`) than what was originally formalized.
3. **Regex Constraints**: The regex enforces strict alphanumeric characters for namespaces (`[a-z0-9]+`) and user segments (`[a-z0-9-]+`), ensuring valid resource IDs downstream. Hyphens are allowed in `project` and `user` but excluded from `namespace`.

## Examples

* `//gbos/bos-platform-dev`
  * IoT Core: _project_: `bos-platform-dev`, _registry_: `UDMI-REFLECT`, _device_: `ZZ-TRI-FECTA`
* `//gbos/bos-platform-dev+debug`
  * Error: user name not supported for provider `gbos`
* `//gbos/bos-platform-dev/faucetsdn`
  * IoT Core: _project_: `bos-platform-dev`, _registry_: `faucetsdn~UDMI-REFLECT`, _device_: `faucetsdn~ZZ-TRI-FECTA`
* `//gref/bos-platform-dev`
  * PubSub topic: `projects/bos-platform-dev/topics/udmi_reflect`
  * PubSub subscription: `projects/bos-platform-dev/subscriptions/udmi_reply+debug` (Defaults to `debug`)
* `//gref/bos-platform-dev+username`
  * PubSub topic: `projects/bos-platform-dev/topics/udmi_reflect`
  * PubSub subscription: `projects/bos-platform-dev/subscriptions/udmi_reply+username`
* `//gref/bos-platform-dev/faucetsdn+username`
  * PubSub topic: `projects/bos-platform-dev/topics/faucetsdn~udmi_reflect`
  * PubSub subscription: `projects/bos-platform-dev/subscriptions/faucetsdn~udmi_reply+username`
* `//mqtt/localhost`
  * MQTT client: `/r/UDMI-REFLECT/d/ZZ-TRI-FECTA`
* `//mqtt/localhost+debug`
  * Error: user name not supported for provider `mqtt`
* `//pubsub/bos-platform-dev`
  * PubSub subscription: `projects/bos-platform-dev/subscriptions/udmi_target+debug`
  * PubSub topic: `projects/bos-platform-dev/topics/udmi_target`
* `//pubsub/bos-platform-dev/faucetsdn+username`
  * PubSub subscription: `projects/bos-platform-dev/subscriptions/faucetsdn~udmi_target+username`
  * PubSub topic: `projects/bos-platform-dev/topics/faucetsdn~udmi_target`
