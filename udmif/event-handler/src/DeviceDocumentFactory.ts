import { DeviceDocument, UdmiMessage } from './model';

const POINTSET_SUB_FOLDER = 'pointset';
const SYSTEM_SUB_FOLDER = 'system';
const MODEL = 'model';
const STATE = 'state';

export function createDeviceDocument(message: UdmiMessage): DeviceDocument {
  if (isSystemState(message)) {
    return createSystemStateDocument(message);
  } else if (isSystemModel(message)) {
    return createSystemModelDocument(message);
  } else {
    return createDefaultDeviceDocument(message);
  }
}

function createSystemModelDocument(message: UdmiMessage): DeviceDocument {
  const deviceDocument: DeviceDocument = createDefaultDeviceDocument(message);

  deviceDocument.section = message.data.location.section;
  deviceDocument.site = message.data.location.site;

  return deviceDocument;
}

function createSystemStateDocument(message: UdmiMessage): DeviceDocument {
  const deviceDocument: DeviceDocument = createDefaultDeviceDocument(message);

  deviceDocument.make = message.data.hardware.make;
  deviceDocument.model = message.data.hardware.model;
  deviceDocument.operational = message.data.operational;
  deviceDocument.serialNumber = message.data.serial_no;
  deviceDocument.firmware = message.data.software.firmware;

  return deviceDocument;
}

function createDefaultDeviceDocument(message: UdmiMessage): DeviceDocument {
  const deviceDocument: DeviceDocument = {
    name: message.attributes.deviceId,
    id: message.attributes.deviceNumId,
  };

  if (message.data.timestamp) {
    deviceDocument.lastPayload = message.data.timestamp;
  }
  return deviceDocument;
}

export function isSystemState(message): boolean {
  return isSubFolder(message, SYSTEM_SUB_FOLDER) && isSubType(message, STATE);
}

export function isSystemModel(message): boolean {
  return isSubFolder(message, SYSTEM_SUB_FOLDER) && isSubType(message, MODEL);
}

export function isSubFolder(message, folderName: string): boolean {
  return message.attributes.subFolder === folderName;
}

export function isSubType(message, type: string): boolean {
  return message.attributes.subType === type;
}
