
## scan_single_past+vendor (PREVIEW)

Check that a scan scheduled in the past never starts

1. Update config before discovery families defined
    * Remove `discovery.families.vendor`
    * Remove `discovery.families.bacnet`
1. Wait for discovery families defined
1. Wait until discovery family keys match
1. Wait for no scans active
1. Update config before scan schedule initially not active
    * Add `discovery.families.vendor` = { "generation": `family generation`, "scan_duration_sec": `10` }
1. Wait until scan schedule initially not active
1. Wait until scan schedule still not active
1. Check that there were no received discovery events

Test passed.
