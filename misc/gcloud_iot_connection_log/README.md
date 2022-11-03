# GCloud IoT Connection Logs

Stores all device connections and disconnection from IoT Core into BigQuery

**NOTE**
- Requires **Cloud Logging** on devices or registry be set to `INFO` or more.
- IoT Core has a default log entries limit of 2000 per second. If a registry has `DEBUG` level logging, this may very quickly be exceeding, and will result in missing connection or disconnection log events

## Installation

`./deploy PROJECT_ID DATASET_ID LOCATION TRIGGER_TOPIC [--drop]`

**NOTE** cloud function deployed with default app-engine service account. This may need to change if this account does not have required permissions

## Example Queries

### List devices which were once connected that now offline 

```sql
 SELECT   *,
         Row_number() OVER(partition BY device_id, registry_id ORDER BY timestamp DESC) AS rank
FROM     `PROJECT_ID.udmi.iot_connects` qualify rank = 1
AND      event = 0
ORDER BY timestamp DESC
```

### List device outages exceeding X minutes

```sql 
SELECT device_id,
       qtimestamp disconnect_time,
       timestamp  reconnect_time,
       outage_minutes
FROM   (
              SELECT device_id,
                     qtimestamp,
                     timestamp,
                     Datetime_diff(timestamp, qtimestamp, minute) AS outage_minutes
              FROM   (
                              SELECT   *,
                                       Lag(timestamp) OVER (partition BY device_id, registry_id ORDER BY timestamp, event) qtimestamp
                              FROM     `PROJECT_ID.udmi.iot_connects`
                              WHERE    logentry = 1
                              AND      event = 1 )
              WHERE  timestamp IS NOT NULL )
WHERE  outage_minutes > 10
```
