
## periodic_scan

1. Update config before all scans not active:
    * Add `discovery` = { "networks": {  } }
1. Wait for all scans not active
1. Update config before scan iterations:
    * Add `discovery.networks.virtual` = { "generation": `network generation`, "scan_interval_sec": `10`, "enumerate": `true` }
1. Wait for scan iterations
