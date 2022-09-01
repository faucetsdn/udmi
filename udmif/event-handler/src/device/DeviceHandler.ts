import { createDeviceDocument, getDeviceKey, getDeviceValidationMessage } from './DeviceDocumentUtils';
import { Handler } from '../Handler';
import { Device, DeviceKey, DeviceValidation } from './model/Device';
import { UdmiMessage } from '../model/UdmiMessage';
import { DAO } from '../dao/DAO';
import { isValidationSubType } from '../MessageUtils';

export class DeviceHandler implements Handler {
  constructor(private deviceDao: DAO<Device>, private deviceValidationDao: DAO<DeviceValidation>) {}

  async handle(udmiMessage: UdmiMessage): Promise<void> {
    const deviceKey: DeviceKey = getDeviceKey(udmiMessage);

    // let's upsert the existing document
    const existingDeviceDocument: Device = await this.deviceDao.get(deviceKey);
    const document: Device = createDeviceDocument(udmiMessage, existingDeviceDocument.points);
    this.deviceDao.upsert(deviceKey, document);

    // if this is a validation message, we will record the validation message
    if (isValidationSubType(udmiMessage)) {
      const deviceValidationMessage: DeviceValidation = getDeviceValidationMessage(udmiMessage, deviceKey);
      this.deviceValidationDao.insert(deviceValidationMessage);
    }
  }
}
