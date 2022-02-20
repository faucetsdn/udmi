/**
 * Simple function to ingest test results event from DAQ.
 */

const functions = require('firebase-functions');
const admin = require('firebase-admin');
const { PubSub } = require(`@google-cloud/pubsub`);
const iot = require('@google-cloud/iot');
const pubsub = new PubSub();
const REFLECT_REGISTRY = 'UDMS-REFLECT';
const DEFAULT_CLOUD_REGION = 'us-central1';
const UDMI_VERSION = '1';
const EVENT_TYPE = 'event';
const CONFIG_TYPE = 'config';
const STATE_TYPE = 'state';

admin.initializeApp(functions.config().firebase);
const db = admin.firestore();

const iotClient = new iot.v1.DeviceManagerClient({
  // optional auth parameters.
});

function currentTimestamp() {
  return new Date().toJSON();
}

function recordMessage(attributes, message) {
  const registryId = attributes.deviceRegistryId;
  const deviceId = attributes.deviceId;
  const subType = attributes.subType || EVENT_TYPE;
  const subFolder = attributes.subFolder || 'unknown';
  const timestamp = (message && message.timestamp) || currentTimestamp();
  const promises = [];
  const collectionType = subType + 's';

  if (message) {
    message.timestamp = timestamp;
    message.version = message.version || UDMI_VERSION;
  }

  console.log('record', registryId, deviceId, subType, subFolder, message);

  const reg_doc = db.collection('registries').doc(registryId);
  promises.push(reg_doc.set({
    'updated': timestamp
  }, { merge: true }));
  const dev_doc = reg_doc.collection('devices').doc(deviceId);
  promises.push(dev_doc.set({
    'updated': timestamp
  }, { merge: true }));
  const config_doc = dev_doc.collection(collectionType).doc(subFolder);
  if (message) {
    promises.push(config_doc.set(message));
  } else {
    promises.push(config_doc.delete());
  }

  const commandFolder = `devices/${deviceId}/${subType}/${subFolder}`;
  promises.push(sendCommand(REFLECT_REGISTRY, registryId, commandFolder, message));

  return promises;
}

function sendCommand(registryId, deviceId, subFolder, message) {
  const projectId = process.env.GCP_PROJECT || process.env.GCLOUD_PROJECT;
  const cloudRegion = DEFAULT_CLOUD_REGION;

  const formattedName =
        iotClient.devicePath(projectId, cloudRegion, registryId, deviceId);

  const messageStr = JSON.stringify(message);
  console.log('command', formattedName, subFolder, messageStr);

  const binaryData = Buffer.from(messageStr);
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
  const subType = attributes.subType || EVENT_TYPE;
  const base64 = event.data;
  const msgString = Buffer.from(base64, 'base64').toString();
  const msgObject = JSON.parse(msgString);

  if (subType != EVENT_TYPE) {
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

  if (subType == 'query') {
    return udmi_query_event(attributes, msgObject);
  }

  target = 'udmi_' + subType;

  return publishPubsubMessage(target, attributes, msgObject);
});

function udmi_query_event(attributes, msgObject) {
  if (attributes.subFolder == 'state') {
    return udmi_query_state(attributes);
  }
  throw 'Unknown query type ' + attributes.subFolder;
}

function udmi_query_state(attributes) {
  const projectId = attributes.projectId;
  const cloudRegion = attributes.cloudRegion || DEFAULT_CLOUD_REGION;
  const registryId = attributes.deviceRegistryId;
  const deviceId = attributes.deviceId;

  const formattedName = iotClient.devicePath(
    projectId,
    cloudRegion,
    registryId,
    deviceId
  );

  console.log('iot query state', formattedName)

  const request = {
    name: formattedName
  };

  return iotClient.getDevice(request).then(deviceData => {
    const stateBinaryData = deviceData[0].state.binaryData;
    const stateString = stateBinaryData.toString();
    const msgObject = JSON.parse(stateString);
    return process_state_update(attributes, msgObject);
  });
}

exports.udmi_state = functions.pubsub.topic('udmi_state').onPublish((event) => {
  const attributes = event.attributes;
  const base64 = event.data;
  const msgString = Buffer.from(base64, 'base64').toString();
  const msgObject = JSON.parse(msgString);

  return process_state_update(attributes, msgObject);
});

function process_state_update(attributes, msgObject) {
  let promises = [];
  const deviceId = attributes.deviceId;
  const registryId = attributes.deviceRegistryId;

  const commandFolder = `devices/${deviceId}/update/states`;

  promises.push(sendCommand(REFLECT_REGISTRY, registryId, commandFolder, msgObject));
  
  attributes.subType = STATE_TYPE;
  for (var block in msgObject) {
    let subMsg = msgObject[block];
    if (typeof subMsg === 'object') {
      attributes.subFolder = block;
      subMsg.timestamp = msgObject.timestamp;
      promises.push(publishPubsubMessage('udmi_target', attributes, subMsg));
      const new_promises = recordMessage(attributes, subMsg);
      promises.push(...new_promises);
    }
  }

  return Promise.all(promises);
};

exports.udmi_config = functions.pubsub.topic('udmi_config').onPublish((event) => {
  const attributes = event.attributes;
  const registryId = attributes.deviceRegistryId;
  const deviceId = attributes.deviceId;
  const subFolder = attributes.subFolder || 'unknown';
  const base64 = event.data;
  const now = Date.now();
  const msgString = Buffer.from(base64, 'base64').toString();

  console.log('udmi_config', registryId, deviceId, subFolder, msgString);
  if (!msgString) {
    console.warn('udmi_config abort', registryId, deviceId, subFolder, msgString);
    return null;
  }
  const msgObject = JSON.parse(msgString);

  attributes.subType = CONFIG_TYPE;

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

  const extraField = message.system && message.system.extra_field;
  const normalJson = extraField !== 'break_json';
  if (!normalJson) {
    console.log('breaking json for test');
  }
  const msgString = normalJson ? JSON.stringify(message) :
        '{ broken because extra_field == ' + message.system.extra_field;
  const binaryData = Buffer.from(msgString);

  const formattedName = iotClient.devicePath(
    projectId,
    cloudRegion,
    registryId,
    deviceId
  );
  console.log('iot modify config', formattedName, msgString);

  const request = {
    name: formattedName,
    versionToUpdate: version,
    binaryData: binaryData
  };
  const commandFolder = `devices/${deviceId}/update/configs`;

  return iotClient
    .modifyCloudToDeviceConfig(request)
    .then(() => sendCommand(REFLECT_REGISTRY, registryId, commandFolder, message));
}

function consolidateConfig(registryId, deviceId) {
  const projectId = process.env.GCP_PROJECT || process.env.GCLOUD_PROJECT;
  const cloudRegion = DEFAULT_CLOUD_REGION;
  const reg_doc = db.collection('registries').doc(registryId);
  const dev_doc = reg_doc.collection('devices').doc(deviceId);
  const configs = dev_doc.collection('configs');

  console.log('consolidating config for', registryId, deviceId);

  const new_config = {
    'version': '1'
  };

  const attributes = {
    projectId: projectId,
    cloudRegion: cloudRegion,
    deviceId: deviceId,
    deviceRegistryId: registryId
  };

  const timestamps = [];

  return configs.get()
    .then((snapshot) => {
      snapshot.forEach(doc => {
        const docData = doc.data();
        const docStr = JSON.stringify(docData);
        console.log('consolidating config with', registryId, deviceId, doc.id, docStr);
        if (docData.timestamp) {
          timestamps.push(docData.timestamp);
          docData.timestamp = undefined;
        }
        new_config[doc.id] = docData;
      });

      if (timestamps.length > 0) {
        new_config['timestamp'] = timestamps.sort()[timestamps.length - 1];
      } else {
        new_config['timestamp'] = 'unknown';
      }

      return update_device_config(new_config, attributes);
    });
}

exports.udmi_update = functions.firestore
  .document('registries/{registryId}/devices/{deviceId}/configs/{subFolder}')
  .onWrite((change, context) => {
    return consolidateConfig(context.params.registryId, context.params.deviceId);
  });

function publishPubsubMessage(topicName, attributes, data) {
  const dataStr = JSON.stringify(data);
  const dataBuffer = Buffer.from(dataStr);
  var attr_copy = Object.assign({}, attributes);

  console.log('publish', topicName, attributes, dataStr);

  return pubsub
    .topic(topicName)
    .publish(dataBuffer, attr_copy)
    .then(messageId => {
      console.debug(`Message ${messageId} published to ${topicName}.`);
    });
}
