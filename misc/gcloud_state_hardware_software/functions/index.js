const {BigQuery} = require('@google-cloud/bigquery');
const bigquery = new BigQuery();

const PROJECT_ID = process.env.PROJECT_ID
const DATASET_ID = process.env.DATASET_ID

exports.stateToBigQuery = async (event, context) => {
  const pubsubMessage = event.data;
  const objStr = Buffer.from(pubsubMessage, 'base64').toString();
  const msg = JSON.parse(objStr);
  
  try {

    const softwareEntries = []
    for (const [key, value] of Object.entries(msg.system.software)) {
      softwareEntries.push({id: key, version:value})
    }

    // Data for devices table
    stateRow = {
      timestamp: BigQuery.timestamp(msg.timestamp),
      device_id: event.attributes.deviceId,
      registry_id: event.attributes.deviceRegistryId,
      make: msg.system.hardware.make,
      model: msg.system.hardware.model,
      serial_no: msg.system.serial_no,
      rev: ("rev" in msg.system.hardware) && msg.system.hardware.rev || null,
      sku: ("sku" in msg.system.hardware) && msg.system.hardware.sku || null,
      software: softwareEntries
    }
    
    return await Promise.all([
      bigquery.dataset(DATASET_ID).table('state').insert([stateRow]),
    ]);
  } catch(err) {
    console.log(err.message);
  }

};

