const PROJECT_ID = process.env.PROJECT_ID
const DATASET_ID = process.env.DATASET_ID
const BUCKET = process.env.BUCKET

const iot = require('@google-cloud/iot');
const {Storage} = require('@google-cloud/storage');
const storage = new Storage();
const iotClient = new iot.v1.DeviceManagerClient({});
const bucket = storage.bucket(BUCKET);
var fs = require('fs');

// projects/daq1-273309/locations/us-central1/registries/ZZ-TRI-FECTA/devices/AHU-1 

exports.storeConfig = async (event, context) => {
  const pubsubMessage = event.data;
  const objStr = Buffer.from(pubsubMessage, 'base64').toString();
  const logData = JSON.parse(objStr);

  devicePath = logData['protoPayload']['resourceName']
  versionToUpdate = logData['protoPayload']['request']['versionToUpdate']

  const [response] = await iotClient.listDeviceConfigVersions({
    name: devicePath,
  });
  
  splitDevice = devicePath.split('/')
  registryId = splitDevice[5]
  deviceId = splitDevice[7]

  const configs = response.deviceConfigs;

  if (configs.length === 0) {
    return null;
  }
  
  configs.forEach((config, index) => { 
    if(config.version == versionToUpdate){
      configPayload = config.binaryData.toString('utf8');
      localFileName = `/tmp/${registryId}_${deviceId}_${versionToUpdate}.txt`

      fs.writeFile(localFileName, configPayload, function (err) {
        console.log(err)
      });


      const options = {
        destination: `${registryId}/${deviceId}/${versionToUpdate}.json`
      };

      bucket.upload(localFileName, options, function(err, file) {
        console.log(err)
      });
      
    }
  });
  
};

