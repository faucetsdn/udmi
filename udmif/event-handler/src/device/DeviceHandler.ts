import { DeviceDocumentFactory } from './DeviceDocumentFactory';
import { Handler } from '../Handler';
import { InvalidMessageError } from '../InvalidMessageError';
import { Device, DeviceKey } from './model/Device';
import { UdmiMessage } from '../model/UdmiMessage';
import { DAO } from '../dao/DAO';

export class DeviceHandler implements Handler {
  constructor(private deviceDao: DAO<Device>, private deviceDocumentFactory: DeviceDocumentFactory) {}

  async handle(udmiMessage: UdmiMessage): Promise<void> {
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

    if (!message.attributes.deviceRegistryId) {
      throw new InvalidMessageError('An invalid site was submitted');
    }

    return { name: message.attributes.deviceId, site: message.attributes.deviceRegistryId };
  }
}
