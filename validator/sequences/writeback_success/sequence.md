
## writeback_success

1. Update config before point filter_differential_pressure_setpoint to have value_state default (null):
    * Remove `pointset.points.filter_differential_pressure_setpoint.set_value`
1. Wait for point filter_differential_pressure_setpoint to have value_state default (null)
1. Update config before point filter_differential_pressure_setpoint to have value_state applied:
    * Add `pointset.points.filter_differential_pressure_setpoint.set_value` = `60`
1. Wait for point filter_differential_pressure_setpoint to have value_state applied
1. Test failed: timeout waiting for point filter_differential_pressure_setpoint to have value_state applied
