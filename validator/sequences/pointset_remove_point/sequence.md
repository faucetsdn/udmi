
## pointset_remove_point (ALPHA)

pointset state does not report unconfigured point

1. Wait for no interesting system status
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points
1. Update config before pointset status does not contain removed point:
    * Remove `pointset.points.filter_differential_pressure_sensor`
1. Wait for pointset status does not contain removed point
1. Wait for no interesting system status
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points
1. Update config before pointset status contains removed point:
    * Add `pointset.points.filter_differential_pressure_sensor` = { "ref": `AV12.present_value`, "units": `Degrees-Celsius` }
1. Wait for pointset status contains removed point
1. Wait for no interesting system status
1. Wait for pointset state reports same points as defined in config
1. Wait for pointset event contains correct points
