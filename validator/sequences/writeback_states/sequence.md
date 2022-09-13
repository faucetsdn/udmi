
## writeback_states

1. Wait for point filter_differential_pressure_sensor to have value_state default (null)
1. Wait for point filter_alarm_pressure_status to have value_state default (null)
1. Wait for point filter_differential_pressure_setpoint to have value_state default (null)
1. Update config to for writeback:
    * Add `pointset.points.filter_alarm_pressure_status.set_value` = `false`
    * Set `pointset.points.filter_differential_pressure_setpoint.set_value` = `60`
    * Add `pointset.points.filter_differential_pressure_sensor.set_value` = `15`
1. Wait for point filter_differential_pressure_sensor to have value_state invalid
1. Wait for point filter_alarm_pressure_status to have value_state failure
1. Wait for point filter_differential_pressure_setpoint to have value_state applied
