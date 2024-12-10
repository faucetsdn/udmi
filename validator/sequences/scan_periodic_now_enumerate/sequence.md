
## scan_periodic_now_enumerate (PREVIEW)

Check periodic scan on a fixed schedule amd enumeration

1. Update config before discovery families defined
    * Remove `discovery.families.vendor`
1. Wait for discovery families defined
1. Wait until discovery family keys match
1. Wait for no scans active
1. Update config before scan iterations
    * Add `discovery.families.vendor` = { "generation": `family generation`, "scan_interval_sec": `20`, "depth": `entries`, "scan_duration_sec": `20` }
1. Wait for scan iterations
1. Check that scan did not terminate prematurely

Test failed: Failed check that all events have matching refs: [Device AHU-1 has extra metadata refs [BV11.present_value, AV12.present_value] and/or extra discovered refs [filter_alarm_pressure_status, filter_differential_pressure_sensor], Device AHU-22 has extra metadata refs [BV11.present_value, AV12.present_value] and/or extra discovered refs [filter_alarm_pressure_status, filter_differential_pressure_sensor]]
