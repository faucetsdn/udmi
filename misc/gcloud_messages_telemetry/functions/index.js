const {BigQuery} = require('@google-cloud/bigquery');
const bigquery = new BigQuery();

const PROJECT_ID = process.env.PROJECT_ID
const DATASET_ID = process.env.DATASET_ID
const MESSAGES_TABLE = process.env.MESSAGES_TABLE
const TELEMETRY_TABLE = process.env.TELEMETRY_TABLE
const STATE_TABLE = process.env.STATE_TABLE

const STATE = 1
const EVENT_POINTSET = 2
const EVENT_SYSTEM = 3
const EVENT_OTHER = 9

const util = require('util')

exports.processMessage = async (event, context) => {
  // Only process messages from device and ignore all message fragments
  if (event.attributes.subType == 'state' && event.attributes.subFolder == 'update') {
    var messageType = STATE
  } else if (!event.attributes.hasOwnProperty('subType')) {
    if (event.attributes.subFolder == 'pointset'){
      var messageType = EVENT_POINTSET;
    } else if (event.attributes.subFolder == 'system'){
      var messageType = EVENT_SYSTEM;
    } else {
      var messageType = EVENT_OTHER
    }
  } else {
    // Not a message from IoT Core
    return;
  }
  const pubsubMessage = event.data;
  const objStr = Buffer.from(pubsubMessage, 'base64').toString('utf8');

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
    device_registry_id: deviceRegistryId,
    message_id: messageId,
    payload_size_bytes: payloadSize,
    message_type: messageType
  };

  promises.push(bigquery.dataset(DATASET_ID).table(MESSAGES_TABLE).insert([messageRow]));
  
  try {
    var msg = JSON.parse(objStr);
  } catch {
    console.log("${deviceRegistryId}/${deviceId} Invalid JSON");
    return await Promise.all(promises);
  }

  // Insert telemetry if pointset event
  // TODO break out into processTelemetry
  // disable temporarily
  if (messageType == EVENT_POINTSET && 'points' in msg){
    var rows = []
    Object.keys(msg.points).forEach(function(key) {
      let row = {
        device_id: deviceId,
        device_num_id: parseInt(event.attributes.deviceNumId),
        message_id: messageId,
        device_registry_id: deviceRegistryId,
        publish_time: publishTimestamp,
        timestamp: BigQuery.timestamp(msg.timestamp),
        point_name: key,
        present_value: (isNaN(parseFloat(msg.points[key].present_value)) ? null : parseFloat(msg.points[key].present_value) ),
        present_value_string: String(msg.points[key].present_value)
      };
        rows.push(row)    
    });

     if (rows.length > 0){
      promises.push(bigquery.dataset(DATASET_ID).table(TELEMETRY_TABLE).insert(rows))
     }

  } else if (messageType == STATE) {
    //TODO break out to processState

    if ("system" in msg === false) {
      return await Promise.all(promises);
    }

    // TODO break out to upgradeState 
    const softwareEntries = []

    // Upgrade software as applicable
    if("firmware" in msg.system && "version" in msg.system.firmware) {
      // Legacy (<1.3.14)
      softwareEntries.push({id: "firmware", version:msg.system.firmware.version});
    } else {
      // Modern (>1.3.14)
      for (const [key, value] of Object.entries(msg.system.software)) {
        if (isString(key) && isString(value)){
          softwareEntries.push({id: key, version:value})
        }
      }
    }
    
    // Upgrade Hardware
    if("make_model" in msg.system && isString(msg.system.make_model)){
      // Legacy (<1.3.14)
      make = "unknown";
      model = msg.system.make_model;
      rev = null;
      sku = null;
    } else {
      make = ("make" in msg.system.hardware && isString(msg.system.hardware.make)) 
        && msg.system.hardware.make || null;
      model = ("model" in msg.system.hardware && isString(msg.system.hardware.model)) 
        && msg.system.hardware.model || null;
      rev = ("rev" in msg.system.hardware && isString(msg.system.hardware.rev)) 
        && msg.system.hardware.rev || null;
      sku = ("sku" in msg.system.hardware && isString(msg.system.hardware.sku)) 
        && msg.system.hardware.sku || null;
    }

    // Data for devices table
    stateRow = {
      timestamp: BigQuery.timestamp(msg.timestamp),
      publish_timestamp: publishTimestamp,
      device_id: event.attributes.deviceId,
      device_num_id: parseInt(event.attributes.deviceNumId),
      gateway_id: gatewayId,
      device_registry_id: event.attributes.deviceRegistryId,
      message_id: messageId,
      make: make,
      model: model,
      serial_no: ("serial_no" in msg.system) && msg.system.serial_no || null,
      rev: rev,
      sku: sku,
      software: softwareEntries
    }

    promises.push(bigquery.dataset(DATASET_ID).table(STATE_TABLE).insert([stateRow]));
  }
  try {
    await Promise.all(promises);
  } catch (err) {
    console.log(util.inspect(err, {showHidden: false, depth: null, colors: true}))
  }
  return 
};

function isString(variable){
  return (typeof variable == "string");
}
