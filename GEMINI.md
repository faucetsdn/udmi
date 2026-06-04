# UDMI Project Instructions

## Bug Triage and Verification Workflow

This project involves complex integrations between Python components (discovery node), Java services (UDMIS/Validator), and middleware (Mosquitto/etcd). To ensure bug fixes are effective and verified:

### 1. Verification of Data Mapping
When data passes between the Python discovery node and the Java UDMIS service via the `reflect` topic:
- **Requirement**: You must verify that the `CloudModel` object in the UDMIS logs contains all expected fields (e.g., `num_id`, `password`).
- **Failure Signature**: The presence of `CREATE iot device ..., false null` in UDMIS logs indicates a mapping failure or missing data in the reflector pipeline.

### 2. Environment Sanitization
Before running any E2E or Integration tests for verification:
- **Requirement**: Strictly follow the cleanup steps in `testing.yml`.
- **Action**: Manually clear Docker networks (`udminet`), volumes, and temporary files in `var/tmp` and `var/etcd` to ensure no stale credentials or configurations persist between runs.

### 3. Log Cross-Referencing
- **Requirement**: Compare the sequence of events in `out/udmis.log` and `out/job-logs.txt` line-by-line.
- **Action**: Any discrepancy in the order of operations (e.g., registration vs. connection attempts) must be investigated as a potential race condition or environmental bias.
