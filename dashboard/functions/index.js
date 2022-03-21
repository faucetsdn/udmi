/**
 * Simple function to ingest test results event from DAQ.
 */

const functions = require('firebase-functions');
const admin = require('firebase-admin');
const { PubSub } = require(`@google-cloud/pubsub`);
const iot = require('@google-cloud/iot');
const pubsub = new PubSub();
const REFLECT_REGISTRY = 'UDMS-REFLECT';
const UDMI_VERSION = '1';
const EVENT_TYPE = 'event';
const CONFIG_TYPE = 'config';
const STATE_TYPE = 'state';

const PROJECT_ID = process.env.GCP_PROJECT || process.env.GCLOUD_PROJECT;
const ALL_REGIONS = ['us-central1', 'europe-west1', 'asia-east1'];
let registry_regions = null;

if (process.env.FIREBASE_CONFIG) {
  admin.initializeApp(functions.config().firebase);
} else {
  console.log('No FIREBASE_CONFIG defined');
}
const db = process.env.FIREBASE_CONFIG ? admin.firestore() : null;

const iotClient = new iot.v1.DeviceManagerClient({
  // optional auth parameters.
});

const registry_promise = getRegistryRegions();

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

  if (db) {
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
  }

  const commandFolder = `devices/${deviceId}/${subType}/${subFolder}`;
  promises.push(sendCommand(REFLECT_REGISTRY, registryId, commandFolder, message));

  return promises;
}

function sendCommand(registryId, deviceId, subFolder, message) {
  return registry_promise.then(() => {
    return sendCommandSafe(registryId, deviceId, subFolder, message);
  });
}

function sendCommandSafe(registryId, deviceId, subFolder, message) {
  const cloudRegion = registry_regions[registryId];

  const formattedName =
        iotClient.devicePath(PROJECT_ID, cloudRegion, registryId, deviceId);

  const messageStr = JSON.stringify(message);
  console.log('command', formattedName, subFolder);

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

function getRegistries(region) {
  console.log('Fetching registries for ' + region);
  const formattedParent = iotClient.locationPath(PROJECT_ID, region);
  return iotClient.listDeviceRegistries({
    parent: formattedParent,
  }).then(result => {
    const registries = result[0];
    console.log('Processing results for ' + region);
    registries.forEach(registry => {
      registry_regions[registry.id] = region;
    });
  });
}

function getRegistryRegions() {
  registry_regions = {};
  promises = [];
  ALL_REGIONS.forEach(region => {
    promises.push(getRegistries(region));
  });
  return Promise.all(promises).then(() => {
    console.log('Fetched ' + Object.keys(registry_regions).length + ' registry regions');
  }).catch(console.error);
}

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

  return registry_promise.then(() => {
    attributes.cloudRegion = registry_regions[attributes.deviceRegistryId];
    if (subType == 'query') {
      return udmi_query_event(attributes, msgObject);
    }
    target = 'udmi_' + subType;
    return publishPubsubMessage(target, attributes, msgObject);
  });
});

function udmi_query_event(attributes, msgObject) {
  if (attributes.subFolder == 'states') {
    return udmi_query_states(attributes);
  }
  throw 'Unknown query type ' + attributes.subFolder;
}

function udmi_query_states(attributes) {
  const projectId = attributes.projectId;
  const registryId = attributes.deviceRegistryId;
  const cloudRegion = attributes.cloudRegion;
  const deviceId = attributes.deviceId;

  const formattedName = iotClient.devicePath(
    projectId,
    cloudRegion,
    registryId,
    deviceId
  );

  console.log('iot query states', formattedName)

  const request = {
    name: formattedName
  };

  return iotClient.getDevice(request).then(deviceData => {
    const stateBinaryData = deviceData[0].state.binaryData;
    const stateString = stateBinaryData.toString();
    const msgObject = JSON.parse(stateString);
    return process_states_update(attributes, msgObject);
  });
}

exports.udmi_state = functions.pubsub.topic('udmi_state').onPublish((event) => {
  const attributes = event.attributes;
  const base64 = event.data;
  const msgString = Buffer.from(base64, 'base64').toString();
  const msgObject = JSON.parse(msgString);

  return process_states_update(attributes, msgObject);
});

function process_states_update(attributes, msgObject) {
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

  console.log('Config message', registryId, deviceId, subFolder, msgString);
  if (!msgString) {
    console.warn('Config abort', registryId, deviceId, subFolder, msgString);
    return null;
  }
  const msgObject = JSON.parse(msgString);

  attributes.subType = CONFIG_TYPE;

  const promises = recordMessage(attributes, msgObject);
  promises.push(publishPubsubMessage('udmi_target', attributes, msgObject));

  promises.push(modify_device_config(registryId, deviceId, subFolder, msgObject));

  return Promise.all(promises);
});

async function modify_device_config(registryId, deviceId, subFolder, subContents) {
  console.log('Config modify subFolder', subFolder);
  const [oldConfig, version] = await get_device_config(registryId, deviceId);
  const message = JSON.parse(oldConfig);
  console.log('Config modify version', version, subFolder);
  if (subContents) {
    message[subFolder] = subContents;
  } else {
    delete message[subFolder];
  }
  const attributes = {
    projectId: PROJECT_ID,
    cloudRegion: registry_regions[registryId],
    deviceId: deviceId,
    deviceRegistryId: registryId
  };
  return update_device_config(message, attributes, version)
    .catch(e => {
      console.log('Config update rejected, retry', subFolder);
      return modify_device_config(registryId, deviceId, subFolder, subContents);
    })
    .then(() => {
      console.log('Config accepted version', version, subFolder);
    });
}

async function get_device_config(registryId, deviceId) {
  await registry_promise;
  const cloudRegion = registry_regions[registryId];

  const devicePath = iotClient.devicePath(
    PROJECT_ID,
    cloudRegion,
    registryId,
    deviceId
  );

  const [response] = await iotClient.listDeviceConfigVersions({
    name: devicePath,
  });

  const configs = response.deviceConfigs;
  if (configs.length === 0) {
    return null;
  }

  return [configs[0].binaryData.toString('utf8'), configs[0].version];
}

function update_device_config(message, attributes, preVersion) {
  const projectId = attributes.projectId;
  const cloudRegion = attributes.cloudRegion;
  const registryId = attributes.deviceRegistryId;
  const deviceId = attributes.deviceId;
  const version = preVersion || 0;

  console.log('Updating config version', version);

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
  console.log('iot modify config version', version, formattedName);

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

function consolidate_config(registryId, deviceId) {
  const cloudRegion = registry_regions[registryId];
  const reg_doc = db.collection('registries').doc(registryId);
  const dev_doc = reg_doc.collection('devices').doc(deviceId);
  const configs = dev_doc.collection('configs');

  console.log('consolidating config for', registryId, deviceId);

  const new_config = {
    'version': '1'
  };

  const attributes = {
    projectId: PROJECT_ID,
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
    const registryId = context.params.registryId;
    const deviceId = constext.params.deviceId;
    return registry_promise.then(consolidate_config(registryId, deviceId));
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
