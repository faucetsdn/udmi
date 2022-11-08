
## writeback_invalid_state

1. Wait for point filter_differential_pressure_sensor to have value_state default (null)
1. Update config before point filter_differential_pressure_sensor to have value_state invalid:
    * Add `pointset.points.filter_differential_pressure_sensor.set_value` = `15`
1. Wait for point filter_differential_pressure_sensor to have value_state invalid
