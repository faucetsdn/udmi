
## writeback_success (ALPHA)

Implements UDMI writeback and can successfully writeback to a point

1. Update config Before target point to have value_state default (null)
    * Remove `pointset.points.filter_differential_pressure_setpoint.set_value`
1. Wait until target point to have value_state default (null)
1. Update config Before target point to have value_state applied
    * Add `pointset.points.filter_differential_pressure_setpoint.set_value` = `60`
1. Wait until target point to have value_state applied
1. Wait until target point to have target expected value
