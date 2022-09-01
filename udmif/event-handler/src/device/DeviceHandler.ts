import { createDevice, getDeviceKey, getDeviceValidation } from './DeviceDocumentUtils';
import { Handler } from '../Handler';
import { Device, DeviceKey, DeviceValidation } from './model/Device';
import { UdmiEvent } from '../model/UdmiEvent';
import { DAO } from '../dao/DAO';
import { isValidationSubType } from '../EventUtils';

export class DeviceHandler implements Handler {
  constructor(private deviceDao: DAO<Device>, private deviceValidationDao: DAO<DeviceValidation>) {}

  async handle(udmiEvent: UdmiEvent): Promise<void> {
    const deviceKey: DeviceKey = getDeviceKey(udmiEvent);

    // let's upsert the existing document
    const existingDeviceDocument: Device = await this.deviceDao.get(deviceKey);
    const document: Device = createDevice(udmiEvent, existingDeviceDocument?.points);
    this.deviceDao.upsert(deviceKey, document);

    // if this is a validation message, we will record the validation message
    if (isValidationSubType(udmiEvent)) {
      const deviceValidation: DeviceValidation = getDeviceValidation(udmiEvent, deviceKey);
      this.deviceValidationDao.insert(deviceValidation);
    }
  }
}
