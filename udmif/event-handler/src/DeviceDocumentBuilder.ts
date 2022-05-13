import { DeviceDocument } from './model';

const POINTSET_SUB_FOLDER = 'pointset';
const SYSTEM_SUB_FOLDER = 'system';
const MODEL = 'model';
const STATE = 'state';

export function buildDeviceDocument(message: any): DeviceDocument {
  const deviceDocument: DeviceDocument = {
    name: message.attributes.deviceId,
    id: message.attributes.deviceNumId,
  };

  if (message.data.timestamp) {
    deviceDocument.lastPayload = message.data.timestamp;
  }

  if (isSystemState(message)) {
    deviceDocument.make = message.data.hardware.make;
    deviceDocument.model = message.data.hardware.model;
    deviceDocument.operational = message.data.operational;
    deviceDocument.serialNumber = message.data.serial_no;
    deviceDocument.firmware = message.data.software.firmware;
  } else if (isSystemModel(message)) {
    deviceDocument.section = message.data.location.section;
    deviceDocument.site = message.data.location.site;
  }

  return deviceDocument;
}

export function isSystemState(message) {
  return message.attributes.subFolder === SYSTEM_SUB_FOLDER && message.attributes.subType === STATE;
}

export function isSystemModel(message) {
  return message.attributes.subFolder === SYSTEM_SUB_FOLDER && message.attributes.subType === MODEL;
}
