
## periodic_scan (ALPHA)

check periodic scan of address families

1. Update config before all scans not active:
    * Add `discovery` = { "families": {  } }
1. Wait for all scans not active
1. Update config before scan iterations:
    * Add `discovery.families.virtual` = { "generation": `family generation`, "scan_interval_sec": `10`, "enumerate": `true` }
1. Wait for scan iterations
