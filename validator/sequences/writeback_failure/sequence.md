
## writeback_failure (ALPHA)

1. Wait for config sync
1. Wait for target point to have value_state default (null)
1. Wait for config sync
1. Update config Before target point to have value_state failure:
    * Add `pointset.points.filter_alarm_pressure_status.set_value` = `false`
1. Wait for target point to have value_state failure
