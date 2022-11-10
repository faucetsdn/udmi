# GCloud Store Messages

Saves a copy of all messages published by a device into GCS

Default bucket is `PROJECTID-iot-messages`

## Installation
`./deploy PROJECT_ID DATASET_ID LOCATION TRIGGER_TOPIC [--drop]`

**NOTE** cloud function deployed with default app-engine service account. This may need to change if this account does not have required permissions