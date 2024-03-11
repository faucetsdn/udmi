
## single_scan_future (ALPHA)

Check results of a single scan scheduled soon

1. Update config before discovery families defined:
    * Add `discovery` = { "families": {  } }
1. Wait for discovery families defined
1. Wait for discovery family keys match
1. Wait for no scans active
1. Update config Before scheduled scan start:
    * Add `discovery.families.vendor` = { "generation": `family generation`, "scan_duration_sec": `10`, "enumerate": `false` }
1. Wait for scheduled scan start
1. Check that scan not started before activation
1. Wait for scheduled scan stop
1. Check that discovery events were received
1. Check that no events have points
