import { DeviceDao } from './DeviceDao';
import { buildDeviceDocument } from './DeviceDocumentBuilder';
import { DeviceDocument, DeviceKey } from './model';

export default class UdmiMessageHandler {
  constructor(private deviceDao: DeviceDao) {}

  handleUdmiEvent(event) {
    try {
      const message = decodeEventData(event);
      const documentKey = getDocumentKey(message);
      const document = buildDeviceDocument(message);
      this.writeDocument(documentKey, document);
    } catch (e) {
      console.error('An unexpected error occurred: ', e);
    }
  }

  private async writeDocument(key: DeviceKey, document: DeviceDocument) {
    // we're using upsert which will allow document updates if it already exists and a document insertion if it does not
    await this.deviceDao.upsert(key, document);
  }
}

export function decodeEventData(event) {
  const stringData = Buffer.from(event.data, 'base64').toString();
  event.data = JSON.parse(stringData);
  console.debug('Decoded event: ', JSON.stringify(event));
  return event;
}

export function getDocumentKey(message): DeviceKey {
  if (!message.attributes.deviceId || !message.attributes.deviceNumId) {
    throw new Error('An invalid device name or id was submitted');
  }

  return { name: message.attributes.deviceId, id: message.attributes.deviceNumId };
}
