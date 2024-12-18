
## writeback_failure (ALPHA)

1. Wait until target point to have value_state default (null)
1. Update config before target point to have value_state failure
    * Add `pointset.points.filter_alarm_pressure_status.set_value` = `false`
1. Wait until target point to have value_state failure

Test passed.
