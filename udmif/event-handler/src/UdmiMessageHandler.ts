import { DeviceDao } from './DeviceDao';
import { Device } from './model/Device';
import { DeviceDocumentFactory } from './DeviceDocumentFactory';
import { UdmiMessage } from './model/UdmiMessage';
import { DeviceKey } from './model/DeviceKey';
import { isPointsetSubType, isSystemSubType, isValidationSubType } from './MessageUtils';
import { InvalidMessageError } from './InvalidMessageError';

/**
 * This will attempt to handle all incoming messages.  Some messages may be filtered out if we do not know how to handle them.
 */
export default class UdmiMessageHandler {
  private VALIDATOR_ID: string = '_validator';

  constructor(private deviceDao: DeviceDao, private deviceDocumentFactory: DeviceDocumentFactory) {}

  async handleUdmiEvent(udmiMessage: UdmiMessage): Promise<void> {
    if (this.messageCanBeHandled(udmiMessage)) {
      console.log('Processing UDMI message: ' + JSON.stringify(udmiMessage));
      await this.handleMessage(udmiMessage);
    } else {
      console.warn('Skipping UDMI message: ' + JSON.stringify(udmiMessage));
    }
  }

  private async handleMessage(udmiMessage: UdmiMessage): Promise<void> {
    const deviceKey: DeviceKey = this.getDeviceKey(udmiMessage);
    if (deviceKey) {
      const existingDeviceDocument: Device = await this.deviceDao.get(deviceKey);
      const document: Device = this.deviceDocumentFactory.createDeviceDocument(
        udmiMessage,
        existingDeviceDocument?.points || []
      );
      this.deviceDao.upsert(deviceKey, document);
    }
  }

  private getDeviceKey(message: UdmiMessage): DeviceKey {
    if (!message.attributes.deviceId) {
      throw new InvalidMessageError('An invalid device id was submitted');
    }

    if (!message.attributes.deviceNumId) {
      throw new InvalidMessageError('An invalid device num id was submitted');
    }

    return { name: message.attributes.deviceId, id: message.attributes.deviceNumId };
  }

  private messageCanBeHandled(message: UdmiMessage): boolean {
    return (
      isPointsetSubType(message) ||
      isSystemSubType(message) ||
      (isValidationSubType(message) && message.attributes.deviceId !== this.VALIDATOR_ID)
    );
  }
}
