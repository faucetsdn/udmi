---
name: gateway-fieldbus-triage
description: Methodological playbook for triaging gateway proxies, sub-device metadata bindings, and attach/communication status.
---

# Gateway & Proxy Triage Playbook

## 1. Principle: Check Gateway Specification & Site Model Bindings
Gateway proxies aggregate multiple physical or virtual sub-devices (e.g., over BACnet or Modbus). Authoritative specs are at:
* `docs/specs/gateway.md`
* `docs/specs/proxy.md`
* `docs/specs/bacnet.md`
* `docs/specs/modbus.md`

## 2. Gateway-Proxy Architecture
* **Gateway Device**: Connects directly to the MQTT broker, authenticates with its own credentials, and proxies telemetry and state on behalf of sub-devices.
* **Proxy Sub-Device**: Does not connect to MQTT directly. In `sites/<site>/devices/<proxy_device>/metadata.json`, it binds to its gateway:
  ```json
  "gateway": {
    "gateway_id": "<GATEWAY_DEVICE_ID>"
  }
  ```
* **Message Routing & Status Reporting** (`docs/specs/gateway.md`):
  - On startup or config update, the gateway attaches to each configured proxy device.
  - **Attach Failures**: If the gateway cannot attach to a proxy device, the failure is reported in the `state.gateway` block of the gateway device's own state message.
  - **Local Communication Errors**: If the gateway attaches successfully but fails to communicate with the proxy device, the failure is indicated in the proxy device's `state.status` block or logged in `events_system` logentries.
  - Config to the proxy device is forwarded by the gateway; state/events for the proxy device are published under the proxy device's ID.

## 3. Investigation Procedure
1. **Validate Site Model Binding**: Call `inspect_site_model(site_model="...", device_id="<proxy_device>")`. Verify that `gateway.gateway_id` points to a valid, existing gateway device in the site model.
2. **Inspect Gateway State**: Run `inspect_message_trace(run_dir="<run_dir>", message_type="state")` for the gateway device to check if its `state.gateway` block reports any attach errors.
3. **Inspect Sub-Device State & Status**: Check the proxy sub-device's `state.status` block and `events_system` logs for communication or timeout errors.
4. **Inspect Test Execution Logs**: Run `get_test_logs(test_id="...")` to examine driver and orchestrator messages during proxy execution.
