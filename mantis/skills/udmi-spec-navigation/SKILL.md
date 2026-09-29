---
name: udmi-spec-navigation
description: Navigational playbook for locating and interpreting authoritative UDMI specifications, message definitions, and JSON schemas in the codebase.
---

# UDMI Specification & Schema Navigation Playbook

## 1. Principle: The Codebase is the Single Source of Truth
Never rely on hardcoded or assumed schema properties, required fields, or message formats. The authoritative definitions reside directly in the UDMI repository. Always traverse the codebase to retrieve current specifications.

## 2. Where Authoritative Knowledge Lives
* **Message Definitions**: `docs/messages/`
  - `docs/messages/config.md`: Cloud-to-device target configuration specification.
  - `docs/messages/state.md`: Device-to-cloud operational state specification.
  - `docs/messages/pointset.md`: Pointset telemetry and state schemas.
  - `docs/messages/system.md`: System status, hardware metadata, and loglevel.
* **Protocol & Feature Specifications**: `docs/specs/`
  - `docs/specs/connecting.md`: Transport protocols, endpoints, and authentication.
  - `docs/specs/gateway.md` & `docs/specs/proxy.md`: Gateway proxying and sub-device topology.
  - `docs/specs/discovery.md`: Network discovery protocols.
  - `docs/specs/sequences/writeback.md`: Actuation and writeback verification.
* **Authoritative JSON Schemas**: `schema/*.json`
  - `schema/config_*.json`: All configuration message schemas.
  - `schema/state_*.json`: All state message schemas.
  - `schema/events_*.json`: All telemetry event schemas.
  - `schema/metadata.json`: Device metadata model schema.
  - `schema/common.json`: Shared definitions (timestamps, operations, enums).

## 3. Investigation Procedure
1. **Locate Relevant Docs**: Use `locate_udmi_doc(topic="<topic>")` to find the exact markdown guide.
2. **Read Specific Sections**: Use `read_udmi_file(file_path="...", start_line=..., end_line=...)` to read the exact specification text.
3. **Inspect Schemas Dynamically**: Use `inspect_udmi_schema(schema_name="<name>", resolve_refs=True)` to inspect the complete, resolved JSON schema contract, including required fields, property types, and pattern constraints.
4. **Inspect Generated Code**: If investigating reflective mapping or serialization, inspect `gencode/java/udmi/schema/*.java` using `search_codebase` or `read_udmi_file`.
