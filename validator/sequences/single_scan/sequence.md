
## single_scan

1. Update config before all scans not active:
    * Add `discovery` = { "families": {  } }
1. Wait for all scans not active
1. Update config before scheduled scan start:
    * Add `discovery.families.virtual` = { "generation": _family generation_, "enumerate": `true` }
1. Wait for scheduled scan start
1. Wait for scan activation
1. Wait for scan completed
