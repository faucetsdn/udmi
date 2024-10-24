
## scan_periodic_now_enumerate (ALPHA)

Check periodic scan on a fixed schedule amd enumeration

1. Wait for config sync
1. Update config before discovery families defined:
    * Add `discovery` = { "families": {  } }
1. Wait for discovery families defined
1. Wait for discovery family keys match
1. Wait for no scans active
1. Wait for config sync
1. Update config before scan iterations:
    * Add `discovery.families.vendor` = { "generation": `family generation`, "scan_interval_sec": `10`, "depth": `entries`, "scan_duration_sec": `10` }
1. Wait for scan iterations
1. Check that scan did not terminate prematurely
1. Check that all events have discovered refs
