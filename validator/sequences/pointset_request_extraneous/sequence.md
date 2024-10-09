
## pointset_request_extraneous (STABLE)

Check error when pointset configuration contains extraneous point

1. Wait for config sync
1. Update config Before pointset state matches config:
    * Add `pointset.sample_rate_sec` = `10`
1. Wait for pointset state matches config
1. Wait for pointset event contains correct points
1. Wait for config sync
1. Update config before pointset state contains extraneous point error:
    * Add `pointset.points[extraneous_point]` = point configuration
1. Wait for pointset state contains extraneous point error
1. Wait for pointset state matches config
1. Wait for pointset event contains correct points
1. Wait for config sync
1. Update config before pointset state removes extraneous point error:
    * Remove `pointset.points[extraneous_point]`
1. Wait for pointset state removes extraneous point error
1. Wait for pointset state matches config
1. Wait for pointset event contains correct points
