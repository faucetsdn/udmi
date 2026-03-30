# State Hardware & Software into BigQuery

Saves the `hardware` and `software` fields from state messages into BigQuery

## Installation
`./deploy PROJECT_ID DATASET_ID LOCATION TRIGGER_TOPIC [--drop]`

**NOTE** cloud function deployed with default app-engine service account. This may need to change if this account does not have required permissions
