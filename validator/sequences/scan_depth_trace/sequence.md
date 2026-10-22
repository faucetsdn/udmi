
## scan_depth_trace+vendor (ALPHA)

Check results of a single scan with trace depth

1. Update config before discovery families defined
    * Remove `discovery.families.vendor`
    * Remove `discovery.families.bacnet`
1. Wait for discovery families defined
1. Wait until discovery family keys match
1. Wait for no scans active
1. Update config before scheduled scan active
    * Add `discovery.families.vendor` = { "generation": `family generation`, "depth": `trace`, "scan_duration_sec": `10` }
1. Wait until scheduled scan active
1. Check that scan started at time
1. Wait until scheduled scan complete
1. Check that scan completed at time
1. Check that trace scan reported positive active_count
1. Check that no discovery events emitted during trace scan
1. Check that received expected number of discovery uploads
1. Check that all discovery uploads match scan family and generation
1. Check that received all unique upload event numbers
1. Check that received proper upload start marker
1. Check that received proper last upload chunk
1. Check that received proper upload termination marker
1. Check that upload chunk 1 data present
1. Check that upload chunk 2 data present
1. Check that upload termination marker includes sha256
1. Check that assembled upload matches termination marker sha256

Test passed.
