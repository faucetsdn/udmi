/**
 * Function suite to handle UDMIS messages using cloud functions.
 */

/**
 * These version markers are used to automatically check that the deployed cloud functions are suitable for any bit
 * of client code. Clients (e.g. sqeuencer) can query this value and check that it's within range. Values
 * indicate the MIN/MAX versions supported, while the client determines what is required.
 *
 * LEVEL 8: Schema refactoring for UDMIS container compatability.
 * LEVEL 9: Dynamic determination of reflect registry.
 */
const FUNCTIONS_VERSION_MIN = 8;
const FUNCTIONS_VERSION_MAX = 9;

// Hacky stuff to work with "maybe have firestore enabled"
const PROJECT_ID = process.env.GCP_PROJECT || process.env.GCLOUD_PROJECT;
const useFirestore = !!process.env.FIREBASE_CONFIG;
if (!process.env.GCLOUD_PROJECT) {
  console.log('Setting GCLOUD_PROJECT to ' + PROJECT_ID);
  process.env.GCLOUD_PROJECT = PROJECT_ID;
}

const version = require('./version');
const functions = require('firebase-functions');
const admin = require('firebase-admin');
const { PubSub } = require('@google-cloud/pubsub');
const pubsub = new PubSub();
const iot = require('@google-cloud/iot');

const UDMI_VERSION = version.udmi_version;

const EVENT_TYPE = 'event';
const CONFIG_TYPE = 'config';
const STATE_TYPE = 'state';
const QUERY_TYPE = 'query';
const REPLY_TYPE = 'reply';
const MODEL_TYPE = 'model';

const UPDATE_FOLDER = 'update';
const CLOUD_FOLDER = 'cloud';
const UDMI_FOLDER = 'udmi';
const ERROR_FOLDER = 'error';

const reflectRegistries = {};

const ALL_REGIONS = ['us-central1', 'europe-west1', 'asia-east1'];
let registryRegions = null;

console.log(`UDMI version ${UDMI_VERSION}, functions ${FUNCTIONS_VERSION_MIN}:${FUNCTIONS_VERSION_MAX}`);
console.log(`Deployed by ${version.deployed_by} at ${version.deployed_at}`);

if (useFirestore) {
  admin.initializeApp(functions.config().firebase);
} else {
  console.log('No FIREBASE_CONFIG defined');
}
const firestore = useFirestore ? admin.firestore() : null;

const iotClient = new iot.v1.DeviceManagerClient({
  // optional auth parameters.
});

const registry_promise = getRegistryRegions();

function currentTimestamp(target) {
  return (target > 0 ? new Date(target) : new Date()).toJSON();
}

function reflectMessage(attributes, message) {
  const registryId = attributes.deviceRegistryId;
  const deviceId = attributes.deviceId;
  const subType = attributes.subType || EVENT_TYPE;
  const subFolder = attributes.subFolder || 'unknown';
  const transactionId = attributes.transactionId;
  const timestamp = (message && message.timestamp) || currentTimestamp();
  const promises = [];

  if (message) {
    message.timestamp = timestamp;
    message.version = message.version || UDMI_VERSION;
  }
  const event_count = message && message.event_count;
  console.log('Message record', registryId, deviceId, subType, subFolder, event_count, transactionId);

  return sendEnvelope(registryId, deviceId, subType, subFolder, message, transactionId);
}

function reflectError(attributes, base64, error) {
  const errorStr = String(error) + " for subFolder " + attributes.subFolder;
  console.log('Captured message error, reflecting:', errorStr);
  console.error('Stack trace:', error);
  const message = {
    error: errorStr,
    data: base64
  }
  attributes.subFolder = ERROR_FOLDER;
  reflectMessage(attributes, message);
}

function sendEnvelope(registryId, deviceId, subType, subFolder, message, transactionId) {
  const reflectRegistry = reflectRegistries[registryId];
  console.log('using reflect registry', registryId, reflectRegistry);
  if (!reflectRegistry) {
    console.log('reflection registry missing for', registryId);
    return;
  }

  console.log('sendEnvelope', registryId, deviceId, subType, subFolder, transactionId);

  const messageStr = (typeof message === 'string') ? message : JSON.stringify(message);
  const base64 = Buffer.from(messageStr).toString('base64');

  envelope = {
    deviceRegistryId: registryId,
    deviceId: deviceId,
    subType: subType,
    subFolder: subFolder,
    transactionId: transactionId,
    payload: base64
  };
  return sendCommand(reflectRegistry, registryId, null, envelope, transactionId);
}

function sendCommand(registryId, deviceId, subFolder, message, transactionId) {
  return sendCommandStr(registryId, deviceId, subFolder, JSON.stringify(message), transactionId);
}

function sendCommandStr(registryId, deviceId, subFolder, messageStr, transactionId) {
  return registry_promise.then(() => {
    return sendCommandSafe(registryId, deviceId, subFolder, messageStr, transactionId);
  });
}

function sendCommandSafe(registryId, deviceId, subFolder, messageStr, transactionId) {
  const cloudRegion = registryRegions[registryId];

  const formattedName =
        iotClient.devicePath(PROJECT_ID, cloudRegion, registryId, deviceId);

  console.log('command', subFolder, formattedName, transactionId);

  const binaryData = Buffer.from(messageStr);
  const request = {
    name: formattedName,
    subfolder: subFolder,
    binaryData: binaryData
  };

  return iotClient.sendCommandToDevice(request)
    .catch(e => {
      console.error('Command error', e.details);
    });
}

exports.udmi_target = functions.pubsub.topic('udmi_target').onPublish((event) => {
  const attributes = event.attributes;
  setReflectRegistry(attributes.deviceRegistryId, attributes.reflectRegistry);
  if (!attributes.deviceId) {
    console.log('Ignoring update with missing deviceId', attributes.deviceRegistryId);
    return null;
  }

  const subType = attributes.subType || EVENT_TYPE;
  if (subType != EVENT_TYPE) {
    return null;
  }
  const base64 = event.data;


  try {
    const msgString = Buffer.from(base64, 'base64').toString();
    const msgObject = JSON.parse(msgString);

    return reflectMessage(attributes, msgObject);
  } catch (e) {
    return reflectError(attributes, base64, e);
  }
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
      registryRegions[registry.id] = region;
    });
  });
}

function getRegistryRegions() {
  registryRegions = {};
  promises = [];
  ALL_REGIONS.forEach(region => {
    promises.push(getRegistries(region));
  });
  return Promise.all(promises).then(() => {
    console.log('Fetched ' + Object.keys(registryRegions).length + ' registry regions');
  }).catch(console.error);
}

function setReflectRegistry(reflectDevice, reflectRegistry) {
  if (reflectRegistry && reflectRegistries[reflectDevice] != reflectRegistry) {
    console.log('Setting reflect registry', reflectDevice, reflectRegistry);
    reflectRegistries[reflectDevice] = reflectRegistry;
  }
}

exports.udmi_reflect = functions.pubsub.topic('udmi_reflect').onPublish((event) => {
  const attributes = event.attributes;
  const base64 = event.data;
  const msgString = Buffer.from(base64, 'base64').toString();
  const msgObject = JSON.parse(msgString);

  if (!attributes.subFolder) {
    return udmi_process_reflector_state(attributes, msgObject);
  }

  if (attributes.subFolder !== 'udmi') {
    console.error('Unexpected subFolder', attributes.subFolder);
    return;
  }

  const reflectRegistry = attributes.deviceRegistryId;
  const reflectDevice = attributes.deviceId;
  setReflectRegistry(reflectDevice, reflectRegistry);

  const envelope = {};
  envelope.projectId = attributes.projectId;
  envelope.deviceRegistryId = msgObject.deviceRegistryId;
  envelope.deviceId = msgObject.deviceId;
  envelope.subFolder = msgObject.subFolder;
  envelope.subType = msgObject.subType;
  envelope.transactionId = msgObject.transactionId;

  const payloadString = Buffer.from(msgObject.payload, 'base64').toString();
  const payload = JSON.parse(payloadString);

  console.log('Reflect', envelope.deviceRegistryId, envelope.deviceId, envelope.subType,
              envelope.subFolder, envelope.transactionId);

  return registry_promise.then(() => {
    envelope.cloudRegion = registryRegions[envelope.deviceRegistryId];
    if (!envelope.cloudRegion) {
      console.error('No cloud region found for target registry', envelope.deviceRegistryId);
      return null;
    }
    if (envelope.subType === QUERY_TYPE) {
      return udmi_query(envelope);
    }
    if (envelope.subType === MODEL_TYPE) {
      return udmi_model(envelope, payload);
    }
    const targetFunction = envelope.subType === 'event' ? 'target' : envelope.subType;
    target = 'udmi_' + targetFunction;
    envelope.reflectRegistry = reflectRegistry;
    return publishPubsubMessage(target, envelope, payload);
  }).catch(e => reflectError(envelope, base64, e));
});

async function udmi_process_reflector_state(attributes, msgObject) {
  // Although not strictly needed for this function, wait for registries to be resolved to avoid startup errors.
  await registry_promise;
  const registryId = attributes.deviceRegistryId;
  const deviceId = attributes.deviceId;

  console.log('Configuring in response to state', registryId, deviceId, JSON.stringify(msgObject));

  const setup = Object.assign({}, version);
  setup.functions_min = FUNCTIONS_VERSION_MIN;
  setup.functions_max = FUNCTIONS_VERSION_MAX;

  const udmiConfig = {};
  udmiConfig.setup = setup;
  udmiConfig.last_state = msgObject.timestamp;

  const deviceConfig = {};
  deviceConfig[UDMI_FOLDER] = udmiConfig;

  {
    // Legacy support for older tools so they get the right info to indicate 'upgrade'!
    deviceConfig['udmis'] = setup;
    setup.last_state = msgObject.timestamp;
  }

  console.log('Setting reflector config', registryId, deviceId, JSON.stringify(deviceConfig));
  const startTime = currentTimestamp();
  return modify_device_config(registryId, deviceId, UPDATE_FOLDER, deviceConfig, startTime, null).
    then(() => propagateReflectConfig(attributes, deviceConfig));
}

function propagateReflectConfig(origAttributes, deviceConfig) {
  const attributes = {};
  attributes.projectId = origAttributes.projectId;
  attributes.deviceRegistryId = origAttributes.deviceId;
  attributes.reflectRegistry = reflectRegistries[origAttributes.deviceId];
  attributes.subFolder = UPDATE_FOLDER;
  attributes.subType = STATE_TYPE;
  console.log('Propagating config to udmi_target & udmi_state', attributes);
  return publishPubsubMessage('udmi_target', attributes, deviceConfig).
    then(() => publishPubsubMessage('udmi_state', attributes, deviceConfig));
}

function udmi_model(attributes, msgObject) {
  const operation = msgObject.operation;
  msgObject.operation = null;
  if (operation === 'CREATE') {
    return udmi_model_create(attributes, msgObject);
  } else if (operation === 'BIND') {
    return udmi_model_bind(attributes, msgObject);
  } else if (operation === 'UPDATE') {
    return udmi_model_update(attributes, msgObject);
  } else if (operation === 'DELETE') {
    return udmi_model_delete(attributes, msgObject);
  } else {
    throw 'Unknown model operation ' + operation;
  }
}

async function udmi_model_create(attributes, msgObject) {
  await registry_promise;
  const projectId = attributes.projectId;
  const registryId = attributes.deviceRegistryId;
  const cloudRegion = registryRegions[registryId];
  const deviceId = attributes.deviceId;

  const registryPath = iotClient.registryPath(
    projectId,
    cloudRegion,
    registryId,
  );

  const device = udmiToIotCoreDevice(msgObject, null)
  device.id = deviceId;
  const request = {
    parent: registryPath,
    device,
  };

  console.log('udmi_model_create', JSON.stringify(registryPath), deviceId);

  const [response] = await iotClient.createDevice(request);

  const message = iotCoreToUdmiDevice(response);

  attributes.subType = REPLY_TYPE;
  message.operation = 'CREATE';
  return reflectMessage(attributes, message);
}

async function udmi_model_bind(attributes, msgObject) {
  await registry_promise;
  const projectId = attributes.projectId;
  const registryId = attributes.deviceRegistryId;
  const cloudRegion = registryRegions[registryId];
  const deviceId = attributes.deviceId;

  const registryPath = iotClient.registryPath(
    projectId,
    cloudRegion,
    registryId,
  );

  const keys = Object.keys(msgObject.device_ids);

  console.log('udmi_model_bind', JSON.stringify(registryPath), keys);

  const promises = [];
  keys.forEach(proxyId => {
    const request = {
      parent: registryPath,
      deviceId: proxyId,
      gatewayId: deviceId,
    };

    const bindPromise = iotClient.bindDeviceToGateway(request)
          .then(response => fetch_cloud_device(attributes))
          .then(device => {
            attributes.subType = REPLY_TYPE;
            device.operation = 'BIND';
            return reflectMessage(attributes, device);
          }).catch(e => {
            return reflectError(attributes, 'bind error', e);
          });
    promises.push(bindPromise);
  });
  return Promise.all(promises);
}

async function udmi_model_update(attributes, msgObject) {
  await registry_promise;
  const projectId = attributes.projectId;
  const registryId = attributes.deviceRegistryId;
  const cloudRegion = registryRegions[registryId];
  const deviceId = attributes.deviceId;

  const devicePath = iotClient.devicePath(
      projectId,
      cloudRegion,
      registryId,
      deviceId
  );

  // See full list of device fields: https://cloud.google.com/iot/docs/reference/cloudiot/rest/v1/projects.locations.registries.devices
  const fieldMask = {
    paths: [
      'credentials',
      'blocked',
      'metadata'
    ],
  };

  const request = {
    device: udmiToIotCoreDevice(msgObject, devicePath),
    updateMask: fieldMask
  };

  console.log('udmi_model_update', JSON.stringify(request));

  const [response] = await iotClient.updateDevice(request);

  const message = iotCoreToUdmiDevice(response);

  attributes.subType = REPLY_TYPE;
  message.operation = 'UPDATE';
  return reflectMessage(attributes, message);
}

async function udmi_model_delete(attributes) {
  await registry_promise;
  const projectId = attributes.projectId;
  const registryId = attributes.deviceRegistryId;
  const cloudRegion = registryRegions[registryId];
  const deviceId = attributes.deviceId;

  const devicePath = iotClient.devicePath(
      projectId,
      cloudRegion,
      registryId,
      deviceId
  );

  const request = {
    name: devicePath
  };

  const device = await fetch_cloud_device(attributes);

  console.log('udmi_model_delete', JSON.stringify(devicePath));

  if (device.device_ids) {
    const promises = [];
    Object.keys(device.device_ids).forEach(proxyId => {
      promises.push(unbind_device(attributes, proxyId));
    });
    await Promise.all(promises);
  }

  const [response] = await iotClient.deleteDevice(request);
  if (Object.keys(response).length !== 0) {
    throw 'Failed delete response: ' + JSON.stringify(response);
  }

  attributes.subType = REPLY_TYPE;
  device.operation = 'DELETE';
  return reflectMessage(attributes, device);
}

async function unbind_device(attributes, proxyId) {
  await registry_promise;
  const projectId = attributes.projectId;
  const registryId = attributes.deviceRegistryId;
  const cloudRegion = registryRegions[registryId];
  const deviceId = attributes.deviceId;

  const registryPath = iotClient.registryPath(
      projectId,
      cloudRegion,
      registryId,
  );

  const request = {
    parent: registryPath,
    deviceId: proxyId,
    gatewayId: deviceId,
  };

  console.log('udmi_model_unbind', JSON.stringify(request));

  return iotClient.unbindDeviceFromGateway(request);
}

function udmi_query(attributes) {
  const subFolder = attributes.subFolder;
  if (subFolder === UPDATE_FOLDER) {
    return udmi_query_state(attributes);
  } else if (subFolder === CLOUD_FOLDER) {
    return udmi_query_cloud(attributes);
  } else {
    throw 'Unknown query folder ' + subFolder;
  }
}

function udmi_query_state(attributes) {
  const transactionId = attributes.transactionId;
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

  console.log('iot query state', formattedName, transactionId)

  const request = {
    name: formattedName
  };

  const queries = [
    iotClient.getDevice(request),
    iotClient.listDeviceConfigVersions(request)
  ];

  return Promise.all(queries).then(([device, config]) => {
    const stateBinaryData = device[0].state && device[0].state.binaryData;
    const stateString = stateBinaryData && stateBinaryData.toString();
    const msgObject = JSON.parse(stateString) || {};
    const lastConfig = config[0].deviceConfigs[0];
    const cloudUpdateTime = lastConfig.cloudUpdateTime.seconds;
    const deviceAckTime = lastConfig.deviceAckTime && lastConfig.deviceAckTime.seconds;
    msgObject.configAcked = String(deviceAckTime >= cloudUpdateTime);
    return process_state_update(attributes, msgObject);
  });
}

function udmi_query_cloud(attributes) {
  return attributes.deviceId ?
    udmi_query_cloud_device(attributes) :
    udmi_query_cloud_registry(attributes);
}

async function udmi_query_cloud_registry(attributes) {
  const devices = await fetch_cloud_registry(attributes);

  const message = {'device_ids': {}};
  devices.forEach(device => message.device_ids[device.id] = {
    num_id: device.numId
  });

  attributes.subType = REPLY_TYPE;
  return reflectMessage(attributes, message);
}

async function fetch_cloud_registry(attributes, gatewayId) {
  await registry_promise;
  const projectId = attributes.projectId;
  const registryId = attributes.deviceRegistryId;
  const cloudRegion = registryRegions[registryId];

  const parentName = iotClient.registryPath(
      projectId,
      cloudRegion,
      registryId
  );

  // See full list of device fields: https://cloud.google.com/iot/docs/reference/cloudiot/rest/v1/projects.locations.registries.devices
  const fieldMask = {
    paths: ['id', 'name', 'num_id']
  };

  const request = {
    parent: parentName,
    gatewayListOptions: {
      associationsGatewayId: gatewayId
    },
    fieldMask,
  };
  const [response] = await iotClient.listDevices(request);
  return response;
}

async function fetch_cloud_device(attributes) {
  await registry_promise;
  const projectId = attributes.projectId;
  const registryId = attributes.deviceRegistryId;
  const cloudRegion = registryRegions[registryId];
  const deviceId = attributes.deviceId;

  const devicePath = iotClient.devicePath(
      projectId,
      cloudRegion,
      registryId,
      deviceId
  );

  // See full list of device fields: https://cloud.google.com/iot/docs/reference/cloudiot/rest/v1/projects.locations.registries.devices
  const fieldMask = {
    paths: [
      'id',
      'name',
      'num_id',
      'credentials',
      'last_heartbeat_time',
      'last_event_time',
      'last_state_time',
      'last_config_ack_time',
      'last_config_send_time',
      'blocked',
      'last_error_time',
      'last_error_status',
      'config',
      'state',
      'log_level',
      'metadata',
      'gateway_config',
    ],
  };

  const [response] = await iotClient.getDevice({
    name: devicePath,
    fieldMask,
  });
  const udmiDevice = iotCoreToUdmiDevice(response);
  if (udmiDevice.is_gateway) {
    console.log('Fetching devices bound to gateway ' + deviceId);
    const devices = await fetch_cloud_registry(attributes, deviceId);
    udmiDevice.device_ids = {};
    devices.forEach(device => udmiDevice.device_ids[device.id] = {
      num_id: device.numId
    });
  }
  return udmiDevice;
}

async function udmi_query_cloud_device(attributes) {
  return fetch_cloud_device(attributes)
    .then(device => {
      attributes.subType = REPLY_TYPE;
      return reflectMessage(attributes, device);
    });
}

function iotCoreToUdmiDevice(core) {
  let lastEventTime = core.lastEventTime &&
      new Date(Number(core.lastEventTime.seconds) * 1000).toISOString();
  return {
    auth_type: core.authType,
    num_id: core.numId,
    blocked: core.blocked,
    metadata: core.metadata,
    last_event_time: lastEventTime,
    is_gateway: core.gatewayConfig && core.gatewayConfig.gatewayType === 'GATEWAY',
    credentials: iotCoreToUdmiCredentials(core.credentials),
  };
}

function udmiToIotCoreDevice(udmi, devicePath) {
  return {
    name: devicePath,
    authType: udmi.auth_type,
    blocked: udmi.blocked,
    metadata: udmi.metadata,
    gatewayConfig: udmiToIotCoreGatewayConfig(udmi.is_gateway),
    credentials: udmiToIotCoreCredentials(udmi.credentials),
  };
}

function udmiToIotCoreGatewayConfig(is_gateway) {
  return {
    gatewayType: is_gateway ? 'GATEWAY' : 'NON_GATEWAY',
    gatewayAuthMethod: 'ASSOCIATION_ONLY'
  }
}

function udmiToIotCoreCredentials(udmi) {
  if (udmi == null) {
    return null;
  }
  const core = [];
  for (let cred of udmi) {
    core.push({
      publicKey: {
        format: udmiToIotCoreKeyFormat(cred.key_format),
        key: cred.key_data
      }
    });
  }
  return core;
}

function iotCoreToUdmiCredentials(core) {
  if (core == null) {
    return null;
  }
  const udmi = [];
  for (let cred of core) {
    const publicKey = cred.publicKey;
    publicKey && udmi.push({
      key_format: iotCoreToUdmiKeyFormat(publicKey.format),
      key_data: publicKey.key
    });
  }
  return udmi;
}

function iotCoreToUdmiKeyFormat(core) {
  return {
    'RSA_PEM': 'RS256',
    'RSA_X509_PEM': 'RS256_X509',
    'ES256_PEM': 'ES256',
    'ES256_X509_PEM': 'ES256_X509'
  }[core];
}

function udmiToIotCoreKeyFormat(udmi) {
  return {
    'RS256': 'RSA_PEM',
    'RS256_X509': 'RSA_X509_PEM',
    'ES256': 'ES256_PEM',
    'ES256_X509': 'ES256_X509_PEM',
  }[udmi];
}

exports.udmi_state = functions.pubsub.topic('udmi_state').onPublish((event) => {
  const attributes = event.attributes;
  setReflectRegistry(attributes.deviceRegistryId, attributes.reflectRegistry);
  if (!attributes.deviceId) {
    console.log('Ignoring update with missing deviceId', attributes.deviceRegistryId);
    return null;
  }
  const base64 = event.data;
  const msgString = Buffer.from(base64, 'base64').toString();
  try {
    const msgObject = JSON.parse(msgString);
    if (attributes.subFolder && attributes.subFolder != UPDATE_FOLDER) {
      attributes.subType = STATE_TYPE;
      return process_state_block(attributes, msgObject);
    } else {
      return process_state_update(attributes, msgObject);
    }
  } catch (e) {
    attributes.subType = STATE_TYPE;
    return reflectError(attributes, base64, e);
  }
});

function process_state_update(attributes, msgObject) {
  let promises = [];
  const deviceId = attributes.deviceId;
  const registryId = attributes.deviceRegistryId;
  const transactionId = attributes.transactionId;

  promises.push(sendEnvelope(registryId, deviceId, STATE_TYPE, UPDATE_FOLDER, msgObject, transactionId));

  attributes.subFolder = UPDATE_FOLDER;
  attributes.subType = STATE_TYPE;
  attributes.reflectRegistry = reflectRegistries[registryId];
  promises.push(publishPubsubMessage('udmi_target', attributes, msgObject));

  const system = msgObject.system;
  const stateStart = system && system.operation && system.operation.last_start;
  stateStart && promises.push(modify_device_config(registryId, deviceId, 'last_start',
      stateStart, currentTimestamp(), null));

  for (var block in msgObject) {
    const subMsg = msgObject[block];
    if (typeof subMsg === 'object') {
      const attrCopy = Object.assign({}, attributes);
      attrCopy.subFolder = block;
      subMsg.timestamp = msgObject.timestamp;
      subMsg.version = msgObject.version;
      promises = promises.concat(process_state_block(attrCopy, subMsg));
    }
  }

  return Promise.all(promises);
};

function process_state_block(attributes, subMsg) {
  console.log('Publishing udmi_target', attributes.subType, attributes.subFolder);
  return reflectMessage(attributes, subMsg).
    then(() => publishPubsubMessage('udmi_target', attributes, subMsg));
}

exports.udmi_config = functions.pubsub.topic('udmi_config').onPublish((event) => {
  const attributes = event.attributes;
  const registryId = attributes.deviceRegistryId;
  const deviceId = attributes.deviceId;
  const subFolder = attributes.subFolder || 'unknown';
  const transactionId = attributes.transactionId;
  const base64 = event.data;
  const now = Date.now();
  const msgString = Buffer.from(base64, 'base64').toString();

  const msgObject = JSON.parse(msgString);

  setReflectRegistry(registryId, attributes.reflectRegistry);

  console.log('Config message', registryId, deviceId, subFolder, transactionId);
  if (!msgString) {
    console.warn('Config abort', registryId, deviceId, subFolder, transactionId);
    return null;
  }

  attributes.subType = CONFIG_TYPE;
  const partialUpdate = subFolder !== UPDATE_FOLDER;

  return modify_device_config(registryId, deviceId, subFolder, msgObject, currentTimestamp(), transactionId).
    then(() => partialUpdate && reflectMessage(attributes, msgObject)).
    then(() => partialUpdate && publishPubsubMessage('udmi_target', attributes, msgObject));
});

function parse_old_config(configStr, resetConfig, deviceId) {
  let config = {};
  try {
    config = JSON.parse(configStr || "{}");
  } catch(e) {
    if (!resetConfig) {
      console.warn('Previous config parse error without reset, ignoring update');
      return null;
    }
    config = {};
  }

  if (resetConfig) {
    const system = config.system;
    const configLastStart = system && system.operation && system.operation.last_start;
    console.warn('Resetting config block', deviceId, configLastStart);

    // Preserve the original structure of the config message for backwards compatibility.
    config = {
      system: {
        operation: {
          last_start: configLastStart
        }
      }
    }
  }

  return config;
}

function update_last_start(config, stateStart) {
  if (!config.system) {
    return false;
  }
  if (!config.system.operation) {
    config.system.operation = {};
  }
  const configLastStart = config.system.operation.last_start;
  const shouldUpdate = stateStart && (!configLastStart || (stateStart > configLastStart));
  console.log('State update last state/config', stateStart, configLastStart, shouldUpdate);
  if (shouldUpdate) {
    config.system.operation.last_start = stateStart;
  }
  return shouldUpdate;
}

async function modify_device_config(registryId, deviceId, subFolder, subContents, startTime, transactionId) {
  const [oldConfig, version] = await get_device_config(registryId, deviceId);
  var newConfig;

  if (subFolder === 'last_start') {
    newConfig = parse_old_config(oldConfig, false, deviceId);
    if (!newConfig || !update_last_start(newConfig, subContents)) {
      return;
    }
  } else if (subFolder === UPDATE_FOLDER) {
    console.log('Config replace version', deviceId, version, startTime, transactionId);
    newConfig = subContents;
    newConfig.timestamp = startTime;
  } else {
    const resetConfig = subFolder === 'system' && subContents && subContents.extra_field === 'reset_config';
    newConfig = parse_old_config(oldConfig, resetConfig, deviceId);
    if (newConfig === null) {
      return;
    }

    newConfig.version = UDMI_VERSION;
    newConfig.timestamp = currentTimestamp();

    console.log('Config modify', deviceId, subFolder, version, startTime, transactionId);
    if (subContents) {
      delete subContents.version;
      delete subContents.timestamp;
      newConfig[subFolder] = subContents;
    } else {
      if (!newConfig[subFolder]) {
        console.log('Config target already null', subFolder, version, startTime);
        return;
      }
      delete newConfig[subFolder];
    }
  }

  const attributes = {
    projectId: PROJECT_ID,
    cloudRegion: registryRegions[registryId],
    deviceId: deviceId,
    transactionId: transactionId,
    deviceRegistryId: registryId
  };

  return update_device_config(newConfig, attributes, version)
    .then(() => {
      console.log('Config accepted', subFolder, version, startTime, transactionId);
    }).catch(e => {
      console.log('Config rejected', subFolder, version, startTime, transactionId);
      return modify_device_config(registryId, deviceId, subFolder, subContents, startTime, transactionId);
    })
}

async function get_device_config(registryId, deviceId) {
  await registry_promise;
  const cloudRegion = registryRegions[registryId];

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
  const transactionId = attributes.transactionId;
  const version = preVersion || 0;

  console.log('Updating config version', version);

  const extraField = message.system && message.system.extra_field;
  const normalJson = extraField !== 'break_json';
  console.log('Config extra field is ' + extraField + ' ' + normalJson);

  const msgString = normalJson ? JSON.stringify(message) :
        '{ broken because extra_field == ' + message.system.extra_field;
  const binaryData = Buffer.from(msgString);

  const formattedName = iotClient.devicePath(
    projectId,
    cloudRegion,
    registryId,
    deviceId
  );
  console.log('iot modify config version', version, formattedName, transactionId);

  const request = {
    name: formattedName,
    versionToUpdate: version,
    binaryData: binaryData
  };

  return iotClient
    .modifyCloudToDeviceConfig(request)
    .then(() => sendEnvelope(registryId, deviceId, CONFIG_TYPE, UPDATE_FOLDER, msgString, transactionId));
}

function consolidate_config(registryId, deviceId, subFolder) {
  const cloudRegion = registryRegions[registryId];
  const reg_doc = firestore.collection('registries').doc(registryId);
  const dev_doc = reg_doc.collection('devices').doc(deviceId);
  const configs = dev_doc.collection(CONFIG_TYPE);

  if (subFolder === UPDATE_FOLDER) {
    return;
  }

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
  .document('registries/{registryId}/devices/{deviceId}/config/{subFolder}')
  .onWrite((change, context) => {
    const registryId = context.params.registryId;
    const deviceId = context.params.deviceId;
    const subFolder = context.params.subFolder;
    return registry_promise.then(consolidate_config(registryId, deviceId, subFolder));
  });

function publishPubsubMessage(topicName, attributes, data) {
  const dataStr = JSON.stringify(data);
  const dataBuffer = Buffer.from(dataStr);
  const deviceId = attributes.deviceId;
  const subType = attributes.subType || EVENT_TYPE;
  const subFolder = attributes.subFolder || 'unknown';
  const transactionId = attributes.transactionId;
  const attrCopy = Object.assign({}, attributes);

  console.log('Message publish', topicName, deviceId, subType, subFolder, transactionId);

  return pubsub
    .topic(topicName)
    .publish(dataBuffer, attrCopy)
    .then(messageId => {
      console.debug(`Message ${messageId} published to ${topicName}.`);
    });
}
