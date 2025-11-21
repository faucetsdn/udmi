
## writeback_success (ALPHA)

Implements UDMI writeback and can successfully writeback to a point

1. Update config before target point has value_state default (null)
    * Set `pointset.sample_rate_sec` = `10`
    * Remove `pointset.points.filter_differential_pressure_setpoint.set_value`
1. Wait until target point has value_state default (null)
1. Update config before target point has value_state applied
    * Add `pointset.points.filter_differential_pressure_setpoint.set_value` = `60`
1. Wait until target point has value_state applied
1. Wait until target point to have target expected value

Test passed.
