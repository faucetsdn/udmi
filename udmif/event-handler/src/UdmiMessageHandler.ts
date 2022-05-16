import { DeviceDao } from './DeviceDao';
import { createDeviceDocument } from './DeviceDocumentFactory';
import { DeviceDocument, DeviceKey, UdmiMessage } from './model';

export default class UdmiMessageHandler {
  constructor(private deviceDao: DeviceDao) {}

  handleUdmiEvent(event: any) {
    try {
      const message: UdmiMessage = decodeEventData(event);
      const deviceKey: DeviceKey = getDeviceKey(message);
      const document: DeviceDocument = createDeviceDocument(message);
      this.writeDocument(deviceKey, document);
    } catch (e) {
      console.error('An unexpected error occurred: ', e);
    }
  }

  private async writeDocument(key: DeviceKey, document: DeviceDocument) {
    await this.deviceDao.upsert(key, document);
  }
}

export function decodeEventData(event: any): UdmiMessage {
  const stringData = Buffer.from(event.data, 'base64').toString();
  event.data = JSON.parse(stringData);
  console.debug('Decoded event: ', JSON.stringify(event));
  return event;
}

export function getDeviceKey(message: UdmiMessage): DeviceKey {
  if (!message.attributes.deviceId || !message.attributes.deviceNumId) {
    throw new Error('An invalid device name or id was submitted');
  }

  return { name: message.attributes.deviceId, id: message.attributes.deviceNumId };
}
