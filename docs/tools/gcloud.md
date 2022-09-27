[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [gcloud](#)

Various notes and tips and tricks for working with GCP. These are more or less hints as to what can be done, but don't expect
them to work out-of-the-box without a deeper understanding of what's going on!

# Viewing subscription

`bin/pull_message`

`gcloud --format=json --project=$project_id pubsub subscriptions pull $subscription --auto-ack`

# View cloud function logs

`bin/cloud_logs $project_id`

`gcloud --project=$project_id functions logs read udmi_config --sort-by=time_utc --limit=1000`

# Update a device's GCP IoT Core configuration

`bin/reset_config`

# Viewing Device GCP Cloud Logging

`bin/gcloud_device_logs PROJECT_ID DEVICE_ID [DEVICE_ID ...]`

```
2022-09-01 14:35:07.677668+00:00  GAT-100    ZZ-TRI-FECTA    PUBLISH STATE (RESOURCE_EXHAUSTED The device "2582332477650079" could not be updated. Device state can be updated only once every 1s.)
2022-09-01 14:35:07.679536+00:00  GAT-100    ZZ-TRI-FECTA    DISCONNECT  (RESOURCE_EXHAUSTED The device "2582332477650079" could not be updated. Device state can be updated only once every 1s.)
2022-09-01 14:35:19.115078+00:00  GAT-100    ZZ-TRI-FECTA    CONNECT 
2022-09-01 14:35:23.637210+00:00  GAT-100    ZZ-TRI-FECTA    SUBSCRIBE /devices/GAT-100/config
2022-09-01 14:35:23.653924+00:00  GAT-100    ZZ-TRI-FECTA    SUBSCRIBE /devices/GAT-100/commands/#
2022-09-01 14:35:23.654129+00:00  GAT-100    ZZ-TRI-FECTA    SUBSCRIBE /devices/GAT-100/errors
2022-09-01 14:35:24.491455+00:00  GAT-100    ZZ-TRI-FECTA    PUBACK CONFIG
2022-09-01 14:35:24.491506+00:00  GAT-100    ZZ-TRI-FECTA    CONFIG 
2022-09-01 14:35:24.632056+00:00  GAT-100    ZZ-TRI-FECTA    PUBLISH STATE
2022-09-01 14:35:25.094994+00:00  ACT-1      ZZ-TRI-FECTA    ATTACH_TO_GATEWAY GAT-100
```
