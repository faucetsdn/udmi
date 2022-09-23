
## periodic_scan

1. Update config:
    * Add `discovery` = { "families": {  } }
1. Wait for all scans not active
1. Update config:
    * Add `discovery.families.virtual` = { "generation": _family generation_, "scan_interval_sec": `10`, "enumerate": `true` }
1. Wait for scan iterations
