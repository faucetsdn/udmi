
## single_scan_future (ALPHA)

Check results of a single scan scheduled soon

1. Wait for config sync
1. Update config before discovery families defined:
    * Add `discovery` = { "families": {  } }
1. Wait for discovery families defined
1. Wait for discovery family keys match
1. Wait for no scans active
1. Wait for config sync
1. Update config Before scheduled scan pending:
    * Add `discovery.families.vendor` = { "generation": `family generation`, "scan_duration_sec": `10` }
1. Wait for scheduled scan pending
1. Wait for scheduled scan active
1. Check that scan start near expected generation time
1. Wait for scheduled scan complete
1. Check that discovery events were received
1. Check that discovery events were valid
1. Check that all scan addresses are unique
1. Check that all expected addresses were found
1. Check that no events have discovered refs
