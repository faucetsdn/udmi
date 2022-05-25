import { DeviceDao } from './DeviceDao';
import { DeviceDocument } from './DeviceDocument';
import { createDeviceDocument } from './DeviceDocumentFactory';
import { UdmiMessage } from './UdmiMessage';
import { DeviceKey } from './DeviceKey';

export default class UdmiMessageHandler {
  constructor(private deviceDao: DeviceDao) {}

  handleUdmiEvent(message: UdmiMessage) {
    try {
      const deviceKey: DeviceKey = getDeviceKey(message);
      const document: DeviceDocument = createDeviceDocument(message);
      this.deviceDao.upsert(deviceKey, document);
    } catch (e) {
      console.error('An unexpected error occurred: ', e);
    }
  }
}

export function getDeviceKey(message: UdmiMessage): DeviceKey {
  if (!message.attributes.deviceId || !message.attributes.deviceNumId) {
    throw new Error('An invalid device name or id was submitted');
  }

  return { name: message.attributes.deviceId, id: message.attributes.deviceNumId };
}
