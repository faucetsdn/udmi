const BUCKET = process.env.BUCKET


const {Storage} = require('@google-cloud/storage');
const storage = new Storage();
const bucket = storage.bucket(BUCKET);
var fs = require('fs');

exports.storeMessage = async (event, context) => {
  const pubsubMessage = event.data;
  const payloadStr = Buffer.from(pubsubMessage, 'base64').toString('utf8');

  const messageId = context.eventId; 
  const publishTimestamp = context.timestamp;
  const deviceId = event.attributes.deviceId;
  const registryId = event.attributes.deviceRegistryId;

  localFileName = `/tmp/${registryId}_${deviceId}_${publishTimestamp}.json`

  fs.writeFile(localFileName, payloadStr, function (err) {
    console.log(err)
    return false
  });

  const options = {
    destination: `${registryId}/${deviceId}/${publishTimestamp}_${messageId}.json`
  };

  bucket.upload(localFileName, options, function(err, file) {
    console.log(err)
    return false
  });
  
  return true
};


