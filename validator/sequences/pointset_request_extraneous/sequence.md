
## pointset_request_extraneous (ALPHA)

pointset configuration contains extraneous point

1. Wait for no interesting system status
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points
1. Update config before pointset status contains extraneous point error:
    * Add `pointset.points.random_1899e18aedb` = {  }
1. Wait for pointset status contains extraneous point error
1. Wait for has interesting system status
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points
1. Update config before pointset status removes extraneous point error:
    * Remove `pointset.points.random_1899e18aedb`
1. Wait for pointset status removes extraneous point error
1. Wait for no interesting system status
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points
