
## scan_single_targeted (ALPHA)

Check results of a single scan targeting specific devices

1. Update config before discovery families defined
    * Remove `discovery.families.vendor`
1. Wait for discovery families defined
1. Wait until discovery family keys match
1. Wait for no scans active
1. Update config before scheduled scan active
    * Add `discovery.families.vendor` = { "generation": `family generation`, "scan_duration_sec": `10`, "addrs": {  } }
1. Wait until scheduled scan active
1. Check that scan started at time
1. Wait until scheduled scan complete
1. Check that scan completed at time

Test failed: Failed check that received expected number of discovery events
