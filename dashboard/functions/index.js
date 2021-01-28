/**
 * Simple function to ingest test results event from DAQ.
 */

const functions = require('firebase-functions');
const admin = require('firebase-admin');
const { PubSub } = require(`@google-cloud/pubsub`);
const iot = require('@google-cloud/iot');
const pubsub = new PubSub();
const REFLECT_REGISTRY = "UDMS-REFLECT";

admin.initializeApp(functions.config().firebase);
const db = admin.firestore();

const iotClient = new iot.v1.DeviceManagerClient({
  // optional auth parameters.
});

function recordMessage(attributes, message) {
  const registryId = attributes.deviceRegistryId;
  const deviceId = attributes.deviceId;
  const subType = attributes.subType || 'events';
  const subFolder = attributes.subFolder || 'unknown';

  const promises = [];
  const timestamp = new Date().toJSON();

  console.log('record', registryId, deviceId, subType, subFolder, typeof message, message);

  const reg_doc = db.collection('registries').doc(registryId);
  promises.push(reg_doc.set({
    'updated': timestamp
  }, { merge: true }));
  const dev_doc = reg_doc.collection('devices').doc(deviceId);
  promises.push(dev_doc.set({
    'updated': timestamp
  }, { merge: true }));
  const config_doc = dev_doc.collection(subType).doc(subFolder);
  promises.push(config_doc.set(message));

  const commandFolder = `devices/${deviceId}/${subFolder}/${subType}`;

  promises.push(sendCommand(REFLECT_REGISTRY, registryId, commandFolder, message));

  return promises;
}

function sendCommand(registryId, deviceId, subFolder, message) {
  const projectId = process.env.GCP_PROJECT || process.env.GCLOUD_PROJECT;
  const cloudRegion = 'us-central1';

  const formattedName =
        iotClient.devicePath(projectId, cloudRegion, registryId, deviceId);

  console.log('command', formattedName, subFolder, message);

  const binaryData = Buffer.from(JSON.stringify(message));
  const request = {
    name: formattedName,
    subfolder: subFolder,
    binaryData: binaryData
  };

  return iotClient.sendCommandToDevice(request)
    .catch((e) => {
      console.error('error sending command:', e.details);
    });
}

exports.udmi_target = functions.pubsub.topic('udmi_target').onPublish((event) => {
  const attributes = event.attributes;
  const subType = attributes.subType || 'events';
  const base64 = event.data;
  const msgString = Buffer.from(base64, 'base64').toString();
  const msgObject = JSON.parse(msgString);

  if (subType != 'events') {
    return null;
  }

  promises = recordMessage(attributes, msgObject);

  return Promise.all(promises);
});

exports.udmi_reflect = functions.pubsub.topic('udmi_reflect').onPublish((event) => {
  const attributes = event.attributes;
  const base64 = event.data;
  const msgString = Buffer.from(base64, 'base64').toString();
  const msgObject = JSON.parse(msgString);

  const parts = attributes.subFolder.split('/');

  attributes.deviceRegistryId = attributes.deviceId;
  attributes.deviceId = parts[1];
  const subType = parts[2];
  attributes.subFolder = parts[3];

  target = 'udmi_' + subType;

  return publishPubsubMessage(target, attributes, msgObject);
});

exports.udmi_state = functions.pubsub.topic('udmi_state').onPublish((event) => {
  const attributes = event.attributes;
  const registryId = attributes.deviceRegistryId;
  const deviceId = attributes.deviceId;
  const base64 = event.data;
  const msgString = Buffer.from(base64, 'base64').toString();
  const msgObject = JSON.parse(msgString);

  let promises = [];

  attributes.subType = 'states';
  for (var block in msgObject) {
    let subMsg = msgObject[block];
    if (typeof subMsg === 'object') {
      attributes.subFolder = block;
      promises.push(publishPubsubMessage('udmi_target', attributes, subMsg));
      const new_promises = recordMessage(attributes, subMsg);
      promises.push(...new_promises);
    }
  }

  return Promise.all(promises);
});

exports.udmi_config = functions.pubsub.topic('udmi_config').onPublish((event) => {
  const attributes = event.attributes;
  const registryId = attributes.deviceRegistryId;
  const deviceId = attributes.deviceId;
  const subFolder = attributes.subFolder || 'unknown';
  const base64 = event.data;
  const now = Date.now();
  const msgString = Buffer.from(base64, 'base64').toString();
  const msgObject = JSON.parse(msgString);

  attributes.subType = 'configs';

  const promises = recordMessage(attributes, msgObject);
  promises.push(publishPubsubMessage('udmi_target', attributes, msgObject));

  return Promise.all(promises);
});

function update_device_config(message, attributes) {
  const projectId = attributes.projectId;
  const cloudRegion = attributes.cloudRegion;
  const registryId = attributes.deviceRegistryId;
  const deviceId = attributes.deviceId;
  const version = 0;

  const msgString = JSON.stringify(message);
  const binaryData = Buffer.from(msgString);

  const formattedName = iotClient.devicePath(
    projectId,
    cloudRegion,
    registryId,
    deviceId
  );

  console.log('iot request', formattedName, msgString);

  const request = {
    name: formattedName,
    versionToUpdate: version,
    binaryData: binaryData
  };

  return iotClient.modifyCloudToDeviceConfig(request);
}

function consolidateConfig(registryId, deviceId) {
  const projectId = process.env.GCP_PROJECT || process.env.GCLOUD_PROJECT;
  const cloudRegion = 'us-central1';
  const reg_doc = db.collection('registries').doc(registryId);
  const dev_doc = reg_doc.collection('devices').doc(deviceId);
  const configs = dev_doc.collection('configs');
  const now = Date.now();
  const timestamp = new Date(now).toJSON();

  console.log('consolidating config for', registryId, deviceId);

  const new_config = {
    'version': '1',
    'timestamp': timestamp
  };

  const attributes = {
    projectId: projectId,
    cloudRegion: cloudRegion,
    deviceId: deviceId,
    deviceRegistryId: registryId
  };

  return configs.get()
    .then((snapshot) => {
      snapshot.forEach(doc => {
        console.log('consolidating config with', registryId, deviceId, doc.id, doc.data());
        new_config[doc.id] = doc.data();
      });
      return update_device_config(new_config, attributes);
    });
}

exports.udmi_update = functions.firestore
  .document('registries/{registryId}/devices/{deviceId}/configs/{subFolder}')
  .onWrite((change, context) => {
    return consolidateConfig(context.params.registryId, context.params.deviceId);
  });

function publishPubsubMessage(topicName, attributes, data) {
  const dataBuffer = Buffer.from(JSON.stringify(data));
  var attr_copy = Object.assign({}, attributes);

  console.log('publish', topicName, attributes, typeof data, data);

  return pubsub
    .topic(topicName)
    .publish(dataBuffer, attr_copy)
    .then(messageId => {
      console.debug(`Message ${messageId} published to ${topicName}.`);
    });
}
