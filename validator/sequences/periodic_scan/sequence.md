
## periodic_scan (ALPHA)

Check periodic scan of address families

1. Update config before all scans not active:
    * Add `discovery` = { "families": {  } }
1. Wait for all scans not active
1. Update config before scan iterations:
    * Add `discovery.families.vendor` = {  }
1. Wait for scan iterations
