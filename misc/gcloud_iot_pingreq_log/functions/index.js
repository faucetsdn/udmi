const {BigQuery} = require('@google-cloud/bigquery');
const bigquery = new BigQuery();

const PROJECT_ID = process.env.PROJECT_ID
const DATASET_ID = process.env.DATASET_ID
const TABLE_NAME = process.env.TABLE_NAME

const PINGREQ = 8;

exports.logPingReq = async (event, context) => {
  const pubsubMessage = event.data;
  const objStr = Buffer.from(pubsubMessage, 'base64').toString();
  const logData = JSON.parse(objStr);

  if (logData['jsonPayload']['eventType'] != "PINGREQ"){
   return;
  }

  log_entry = {
    timestamp: logData['timestamp'],
    device_id: logData['labels']['device_id'],
    device_num_id: logData['resource']['labels']['device_num_id'],
    device_registry_id: logData['resource']['labels']['device_registry_id'],
    message_type: PINGREQ,
    payload_bytes: 0
  }
  
  return await Promise.all([
    bigquery.dataset(DATASET_ID).table(TABLE_NAME).insert([log_entry]),
  ]);
  
};

