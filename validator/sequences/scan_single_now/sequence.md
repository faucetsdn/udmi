
## scan_single_now (PREVIEW)

Check results of a single scan scheduled in the recent past

1. Update config before discovery families defined
    * Remove `discovery.families.vendor`
1. Wait for discovery families defined
1. Wait until discovery family keys match
1. Wait for no scans active
1. Update config Before scheduled scan active
    * Add `discovery.families.vendor` = { "generation": `family generation`, "scan_duration_sec": `10` }
1. Wait until scheduled scan active
1. Check that scan started at time
1. Wait until scheduled scan complete
1. Check that scan completed at time
1. Check that discovery events were received
1. Check that no events have discovered refs
1. Check that discovery events were valid
1. Check that all scan addresses are unique
1. Check that all expected addresses were found

Test passed.
