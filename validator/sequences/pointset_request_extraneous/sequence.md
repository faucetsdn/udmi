
## pointset_request_extraneous (BETA)

pointset configuration contains extraneous point

1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value
1. Update config before pointset status contains extraneous point error:
    * Add `pointset.points[extraneous_point]` = point configuration
1. Wait for pointset status contains extraneous point error
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value
1. Update config before pointset status removes extraneous point error:
    * Remove `pointset.points[extraneous_point]`
1. Wait for pointset status removes extraneous point error
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points with present_value
