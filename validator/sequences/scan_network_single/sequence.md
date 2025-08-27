
## scan_network_single+vendor (ALPHA)

Check results of a single scan targeting specific devices

1. Update config before discovery families defined
    * Remove `discovery.families.vendor`
    * Remove `discovery.families.bacnet`
1. Wait for discovery families defined
1. Wait until discovery family keys match
1. Wait for no scans active
1. Update config before scheduled scan active
    * Add `discovery.families.vendor` = { "generation": `family generation`, "depth": `details`, "scan_duration_sec": `10`, "networks": `list of target networks` }
1. Wait until scheduled scan active
1. Check that scan started at time
1. Wait until scheduled scan complete
1. Check that scan completed at time
1. Check that received expected number of discovery events
1. Check that all events have matching refs
1. Check that discovery events were valid
1. Check that received all unique event numbers
1. Check that received proper discovery start event
1. Check that received proper last discovery event
1. Check that received proper discovery termination event
1. Check that all scan addresses are unique
1. Check that all expected addresses were found
1. Check that all expected networks were found

Test passed.
