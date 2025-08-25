
## writeback_operation (ALPHA)

Tests intermediate UPDATING state of a writeback operation

1. Update config before target point has value_state default (null)
    * Remove `pointset.points.filter_differential_pressure_setpoint.set_value`
1. Wait until target point has value_state default (null)
1. Update config before target point has value_state updating
    * Add `pointset.points.filter_differential_pressure_setpoint.set_value` = `60`
1. Wait until target point has value_state updating
1. Wait until target point has value_state applied

Test passed.
