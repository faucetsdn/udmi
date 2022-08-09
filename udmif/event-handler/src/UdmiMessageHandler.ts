import { DeviceDao } from './DeviceDao';
import { DeviceDocument } from './DeviceDocument';
import { createDeviceDocument } from './DeviceDocumentFactory';
import { UdmiMessage } from './UdmiMessage';
import { DeviceKey } from './DeviceKey';
import { isPointsetSubType, isSystemSubType } from './MessageUtils';

export default class UdmiMessageHandler {
  constructor(private deviceDao: DeviceDao) { }

  async handleUdmiEvent(udmiMessage: UdmiMessage): Promise<void> {
    try {
      if (this.messageCanBeHandled(udmiMessage)) {
        await this.handleMessage(udmiMessage);
      } else {
        console.warn('Could not handle the following message: ' + JSON.stringify(udmiMessage));
      }
    } catch (e) {
      console.error('An unexpected error occurred: ', e);
    }
  }

  private async handleMessage(udmiMessage: UdmiMessage): Promise<void> {
    const deviceKey: DeviceKey = this.getDeviceKey(udmiMessage);
    if (deviceKey) {
      const existingDeviceDocument: DeviceDocument = await this.deviceDao.get(deviceKey);
      const document: DeviceDocument = createDeviceDocument(udmiMessage, existingDeviceDocument?.points || []);
      this.deviceDao.upsert(deviceKey, document);
    }
  }

  private getDeviceKey(message: UdmiMessage): DeviceKey {
    if (!message.attributes.deviceId || !message.attributes.deviceNumId) {
      throw new Error('An invalid device name or id was submitted');
    }

    return { name: message.attributes.deviceId, id: message.attributes.deviceNumId };
  }

  private messageCanBeHandled(message: UdmiMessage): boolean {
    return isPointsetSubType(message) || isSystemSubType(message);
  }
}
