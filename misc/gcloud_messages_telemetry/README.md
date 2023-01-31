# Messages and Telemetry to BigQuery

Saves a record of all messages into BigQuery, as well as telemetry into a narrow data structure

## Installation

`./deploy PROJECT_ID DATASET_ID LOCATION TRIGGER_TOPIC [--drop]`

**NOTE** cloud function deployed with default app-engine service account. This may need to change if this account does not have required permissions

## BigQuery Tables

- `messages` record of messages
- `telemetry` telemetry
