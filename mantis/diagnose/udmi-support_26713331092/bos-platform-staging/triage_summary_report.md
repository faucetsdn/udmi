# Mantis AI Diagnostics: Triage Summary Report

| Execution Metadata | Value |
| :--- | :--- |
| **Target Project** | `//udmi-support/26713331092` |
| **Site ID** | `bos-platform-staging` |
| **Triage Timestamp** | `2026-06-01 08:44:14 UTC` |

> [!NOTE]
> ### Diagnostic Performance Metrics
> * **Total Failures Evaluated:** `12`
> * **Diagnostic Resolution Rate:** `100%` (12 successfully isolated)
> * **Data Limitation Abort Rate:** `0%` (0 incomplete cases)

## Failed Test Diagnostics Breakdown
| Test Case | Suite | Category | Breakpoint & Root Cause Isolation Summary | Link to Analysis |
| :--- | :--- | :--- | :--- | :--- |
| `bad_point_ref` | `itemized` | `gateway` | Triage complete. Review details. | [View Analysis](./AHU-1/bad_point_ref/triage_analysis.md) |
| `system_min_loglevel` | `itemized` | `system` | Triage complete. Review details. | [View Analysis](./AHU-1/system_min_loglevel/triage_analysis.md) |
| `pointset_remove_point` | `itemized` | `pointset` | Triage complete. Review details. | [View Analysis](./AHU-1/pointset_remove_point/triage_analysis.md) |
| `broken_config.status` | `itemized` | `system` | Triage complete. Review details. | [View Analysis](./AHU-1/broken_config.status/triage_analysis.md) |
| `broken_config.logging` | `itemized` | `system` | Triage complete. Review details. | [View Analysis](./AHU-1/broken_config.logging/triage_analysis.md) |
| `system_last_update.subblocks` | `itemized` | `system` | Triage complete. Review details. | [View Analysis](./AHU-1/system_last_update.subblocks/triage_analysis.md) |
| `device_config_acked` | `itemized` | `system` | Triage complete. Review details. | [View Analysis](./AHU-1/device_config_acked/triage_analysis.md) |
| `broken_config` | `itemized` | `system` | Triage complete. Review details. | [View Analysis](./AHU-1/broken_config/triage_analysis.md) |
| `writeback_success` | `itemized` | `writeback` | Triage complete. Review details. | [View Analysis](./AHU-1/writeback_success/triage_analysis.md) |
| `system_last_update` | `itemized` | `system` | Triage complete. Review details. | [View Analysis](./AHU-1/system_last_update/triage_analysis.md) |
| `system_min_loglevel` | `sequencer` | `system` | Triage complete. Review details. | [View Analysis](./AHU-1/system_min_loglevel/triage_analysis.md) |
| `writeback_operation` | `itemized` | `writeback` | Triage complete. Review details. | [View Analysis](./AHU-1/writeback_operation/triage_analysis.md) |

---
## Failure Signature Clustering
Failure clusters group individual regressions sharing similar root-cause patterns or breakpoint profiles.

### General Unclassified Regression Signature (Affecting 12 Tests)
- `bad_point_ref` ([Triage Details](./AHU-1/bad_point_ref/triage_analysis.md))
- `system_min_loglevel` ([Triage Details](./AHU-1/system_min_loglevel/triage_analysis.md))
- `pointset_remove_point` ([Triage Details](./AHU-1/pointset_remove_point/triage_analysis.md))
- `broken_config.status` ([Triage Details](./AHU-1/broken_config.status/triage_analysis.md))
- `broken_config.logging` ([Triage Details](./AHU-1/broken_config.logging/triage_analysis.md))
- `system_last_update.subblocks` ([Triage Details](./AHU-1/system_last_update.subblocks/triage_analysis.md))
- `device_config_acked` ([Triage Details](./AHU-1/device_config_acked/triage_analysis.md))
- `broken_config` ([Triage Details](./AHU-1/broken_config/triage_analysis.md))
- `writeback_success` ([Triage Details](./AHU-1/writeback_success/triage_analysis.md))
- `system_last_update` ([Triage Details](./AHU-1/system_last_update/triage_analysis.md))
- `system_min_loglevel` ([Triage Details](./AHU-1/system_min_loglevel/triage_analysis.md))
- `writeback_operation` ([Triage Details](./AHU-1/writeback_operation/triage_analysis.md))

---
## Pull Request Comment Snippet
```markdown
### Mantis AI Debugger isolated 12 regressions in this test run:
- ❌ **bad_point_ref**: Triage complete. Review details.
- ❌ **system_min_loglevel**: Triage complete. Review details.
- ❌ **pointset_remove_point**: Triage complete. Review details.
- ❌ **broken_config.status**: Triage complete. Review details.
- ❌ **broken_config.logging**: Triage complete. Review details.
- ❌ **system_last_update.subblocks**: Triage complete. Review details.
- ❌ **device_config_acked**: Triage complete. Review details.
- ❌ **broken_config**: Triage complete. Review details.
- ❌ **writeback_success**: Triage complete. Review details.
- ❌ **system_last_update**: Triage complete. Review details.
- ❌ **system_min_loglevel**: Triage complete. Review details.
- ❌ **writeback_operation**: Triage complete. Review details.
```