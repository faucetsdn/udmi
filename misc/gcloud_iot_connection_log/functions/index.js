const {BigQuery} = require('@google-cloud/bigquery');
const bigquery = new BigQuery();

const PROJECT_ID = process.env.PROJECT_ID
const DATASET_ID = process.env.DATASET_ID

exports.logConnectionEvents = async (event, context) => {
  const pubsubMessage = event.data;
  const objStr = Buffer.from(pubsubMessage, 'base64').toString();
  const logData = JSON.parse(objStr);

  if (logData['jsonPayload']['eventType'] == "CONNECT"){
    oldState = 0;
    newState = 1;
  } else if (logData['jsonPayload']['eventType']  == "DISCONNECT") {
    newState = 0;
    oldState = 1;
  } else {
    return;
  }

  if (logData['jsonPayload']['status']['description'] == "OK" ){
    description = null;
    message = null;
  } else {
    description = logData['jsonPayload']['status']['description'];
    message = logData['jsonPayload']['status']['message'];
  }

  // reduce nanoseconds to microseconds
  ts_ts = logData['timestamp'].substring(0,19)
  ts_ns = logData['timestamp'].substring(20,29)
  ms = Math.floor(parseInt(ts_ns) / 1000).padStart(6, '0')
  ts = ts_ts + '.' + String(ms).padStart(6, '0') + 'Z';

  // Order events by timestamp
  // We don't need microsecond accuracy, so either add or subtract 1 microsecond
  // to avoid dealing with dates and set such that t1 < t2
  if (ms == 0) {
    t1 = ts
    t2 = ts_ts + '.000001Z';
  } else {
    t2 = ts;
    t1 = ts_ts + '.' + String(ms - 1).padStart(6, '0') + 'Z';
  }


// the log event is always the newer entry
log_entry = {
    timestamp: t2,
    device_id: logData['labels']['device_id'],
    device_num_id: logData['resource']['labels']['device_num_id'],
    device_registry_id: logData['resource']['labels']['device_registry_id'],
    event: newState,
    logentry: 1,
    logentry_description: description,
    logentry_message: message
  }


  // the "derivative" is always preceding entry 
  log_derivative = {
    timestamp: t1,
    device_id: logData['labels']['device_id'],
    device_num_id: logData['resource']['labels']['device_num_id'],
    device_registry_id: logData['resource']['labels']['device_registry_id'],
    event: oldState,
    logentry: 0
  }
  
  return await Promise.all([
    bigquery.dataset(DATASET_ID).table('iot_connects').insert([log_derivative, log_entry]),
  ]);

};

