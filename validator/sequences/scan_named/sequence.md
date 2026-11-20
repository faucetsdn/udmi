
## scan_named+vendor (ALPHA)

Check a named scan reports its own state and attributes its results

1. Update config before discovery families defined
    * Remove `discovery.families.vendor`
    * Remove `discovery.families.bacnet`
1. Wait for discovery families defined
1. Wait until discovery family keys match
1. Wait for no scans active
1. Update config before family scan pending
    * Add `discovery.scans` = { "sequencer_scan": { "generation": `scan generation`, "depth": `entries`, "scan_duration_sec": `10`, "family": `vendor` } }
    * Add `discovery.families.vendor` = { "generation": `future family generation` }
1. Wait until family scan pending
1. Wait until named scan active
1. Wait until named scan complete
1. Check that family scan still pending
1. Check that named scan has no error status
1. Check that named scan reported active count
1. Check that received expected number of discovery events
1. Check that all events attributed to named scan
1. Check that received all unique event numbers
1. Check that received proper discovery start event
1. Check that received proper last discovery event
1. Check that received proper discovery termination event
1. Check that all expected addresses were found
1. Update config before named scan state removed
    * Remove `discovery.scans`
1. Wait for named scan state removed

Test passed.
