
## writeback_invalid (ALPHA)

1. Wait for config sync
1. Wait for target point to have value_state default (null)
1. Wait for config sync
1. Update config Before target point to have value_state invalid:
    * Add `pointset.points.filter_differential_pressure_sensor.set_value` = `15`
1. Wait for target point to have value_state invalid
