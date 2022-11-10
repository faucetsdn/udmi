const {BigQuery} = require('@google-cloud/bigquery');
const bigquery = new BigQuery();

const PROJECT_ID = process.env.PROJECT_ID
const DATASET_ID = process.env.DATASET_ID
const MESSAGES_TABLE = process.env.MESSAGES_TABLE
const TELEMETRY_TABLE = process.env.TELEMETRY_TABLE

const STATE = 1
const EVENT_POINTSET = 2
const EVENT_SYSTEM = 3
const EVENT_DISCOVERY = 4
const EVENT_OTHER = 9

exports.processMessage = async (event, context) => {
  // Only process messages from device and ignore all message fragments
  if (event.attributes.subType == 'state' && event.attributes.subFolder == 'update') {
    var messageType = STATE
  } else if (!event.attributes.hasOwnProperty('subType')) {
    if (event.attributes.subFolder == 'pointset'){
      var messageType = EVENT_POINTSET;
    } else if (event.attributes.subFolder == 'pointset'){
      var messageType = EVENT_SYSTEM;
    } else {
      var messageType = EVENT_OTHER
    }
  } else {
    return;
  }
  const pubsubMessage = event.data;
  const objStr = Buffer.from(pubsubMessage, 'base64').toString('utf8');
  const msgObj = JSON.parse(objStr);

  // This is a Pub/Sub message ID -  messages copied in the stack 
  // will have different message IDs
  const messageId = context.eventId; 

  const publishTimestamp = BigQuery.timestamp(context.timestamp);
  const deviceId = event.attributes.deviceId;
  const gatewayId = ("gatewayId" in event.attributes) && event.attributes.gatewayId || null;
  const deviceRegistryId = event.attributes.deviceRegistryId;

  const payloadSize = Buffer.byteLength(objStr, 'utf8');

  var promises = [];

  // add message to table
  var messageRow = {
    publish_timestamp: publishTimestamp,
    device_num_id: parseInt(event.attributes.deviceNumId),
    device_id: deviceId,
    gateway_id: gatewayId,
    registry_id: deviceRegistryId,
    message_id: messageId,
    payload_size_bytes: payloadSize,
    message_type: messageType
  };

  promises.push(bigquery.dataset(DATASET_ID).table(MESSAGES_TABLE).insert([messageRow]));
  
  // Insert Telemetry
  if ('points' in msgObj){
    var rows = []
    const payloadTimestamp = BigQuery.timestamp(msgObj.timestamp);
    Object.keys(msgObj.points).forEach(function(key) {
      let row = {
        device_id: deviceId,
        device_num_id: parseInt(event.attributes.deviceNumId),
        message_id: messageId,
        gateway_id: gatewayId,
        registry_id: deviceRegistryId,
        publish_timestamp: publishTimestamp,
        timestamp: payloadTimestamp,
        point_name: key,
        present_value: (isNaN(parseFloat(msgObj.points[key].present_value)) ? null : parseFloat(msgObj.points[key].present_value) ),
        present_value_string: String(msgObj.points[key].present_value)
      };
        rows.push(row)    
    });

     if (rows.length > 0){
      promises.push(bigquery.dataset(DATASET_ID).table(TELEMETRY_TABLE).insert(rows))
     }
   
  }
 
  return await Promise.all(promises);
};
