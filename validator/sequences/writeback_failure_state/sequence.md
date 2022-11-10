
## writeback_failure_state

1. Wait for point filter_alarm_pressure_status to have value_state default (null)
1. Update config before point filter_alarm_pressure_status to have value_state failure:
    * Add `pointset.points.filter_alarm_pressure_status.set_value` = `false`
1. Wait for point filter_alarm_pressure_status to have value_state failure
