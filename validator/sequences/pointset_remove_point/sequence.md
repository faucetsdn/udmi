
## pointset_remove_point (STABLE)

Check that pointset state does not report an unconfigured point

1. Wait for config sync
1. Wait for pointset state matches config
1. Wait for pointset event contains correct points
1. Wait for config sync
1. Update config before pointset state does not contain removed point:
    * Remove `pointset.points[random_point]`
1. Wait for pointset state does not contain removed point
1. Wait for pointset state matches config
1. Wait for pointset event contains correct points
1. Wait for config sync
1. Update config before pointset state contains restored point:
    * Add `pointset.points[random_point]` = point configuration
1. Wait for pointset state contains restored point
1. Wait for pointset state matches config
1. Wait for pointset event contains correct points
