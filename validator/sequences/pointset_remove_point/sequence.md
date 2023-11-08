
## pointset_remove_point (BETA)

pointset state does not report unconfigured point

1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value
1. Update config before pointset status does not contain removed point:
    * Remove `pointset.points[random_point]`
1. Wait for pointset status does not contain removed point
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value
1. Update config before pointset status contains removed point:
    * Add `pointset.points[random_point]` = point configuration
1. Wait for pointset status contains removed point
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value
