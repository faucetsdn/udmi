[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [gcloud](#)

Various notes and tips and tricks for working with GCP. These are more or less hints as to what can be done, but don't expect
them to work out-of-the-box without a deeper understanding of what's going on!

# Viewing subscription

`bin/pull_message:gcloud --format=json --project=$project_id pubsub subscriptions pull $subscription --auto-ack`

# View cloud function logs

`gcloud --project=$project_id functions logs read udmi_config --sort-by=time_utc --limit=1000`

# Update a device's GCP IoT Core configuration

`bin/reset_config`
