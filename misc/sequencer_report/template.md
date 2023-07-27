# {{report.device_id}}

## Device Identification

| Device | AHU-1 |
|---|---|
| Site | ZZ-TRI-FECTA |
| Make | BOS |
| Model | Pubber |
| Software | firmware: v1 |


Information about the device from metadata
## Summary

|  | Feature | Stable | Beta | Alpha |
|---|---|---|---|---|
| Y | system | 2/2 | 0/1 | 0/0 | 
| N | writeback | 0/1 | 0/0 | 0/0 | 
| - | discovery | 0/0 | 0/0 | 0/1 | 
Overall summary table, sorted by bucket alphabetically

Y/N based on Stable results only.

X/Y Based on “Score” - Use default of 1 for everything initially.

Only generated when a complete run is performed. Does not use previous results.

Alpha results column included for reporting ONLY WHEN -a option is used
## Results

| Bucket | Feature | Stage | Score | Result | Description |
|---|---|---|---|---|---|---|
| discovery.scan | periodic_scan | ALPHA | 1 | fail | Failed waiting for scan |
| system | device_config_acked | STABLE | 1 | pass | Sequence complete |
| system | broken_config | STABLE | 0 | pass | sequence complete |
| system | other_test | BETA | 0 | pass | sequence complete |
| writeback | writeback_success | STABLE | 1 | pass | Sequence complete |
| discovery.scan | periodic_scan | ALPHA | 1 | pass | Sequence complete |


List of results, sorted by feature bucket alphabetically





