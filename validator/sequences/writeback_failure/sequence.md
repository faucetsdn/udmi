
## writeback_failure (ALPHA)

1. Wait until target point has value_state default (null)
1. Update config Before target point has value_state failure
    * Add `pointset.points.filter_alarm_pressure_status.set_value` = `false`
1. Wait until target point has value_state failure

Test passed.
