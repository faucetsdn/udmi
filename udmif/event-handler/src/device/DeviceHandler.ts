import { createDevice, getDeviceKey, getDeviceValidation } from './DeviceDocumentUtils';
import { Handler } from '../Handler';
import { Device, DeviceKey, DeviceValidation, PRIMARY_KEYS } from './model/Device';
import { UdmiEvent } from '../udmi/UdmiEvent';
import { DAO } from '../dao/DAO';
import { isValidationSubType } from '../EventUtils';
import { Point } from './model/Point';

export class DeviceHandler implements Handler {
  constructor(
    private devicePgDao: DAO<Device>,
    private deviceValidationPgDao: DAO<DeviceValidation>
  ) { }

  async handle(udmiEvent: UdmiEvent): Promise<void> {

    const deviceKey: DeviceKey = getDeviceKey(udmiEvent);
    const existingDeviceDocumentFromPg: Device = await this.devicePgDao.get(deviceKey);
    const pgDevice: Device = createDevice(udmiEvent, existingDeviceDocumentFromPg?.points as Point[]);

    // let's upsert the existing document
    await this.devicePgDao.upsert(pgDevice, PRIMARY_KEYS);

    // if this is a validation message, we will record the validation message
    if (isValidationSubType(udmiEvent)) {
      const deviceValidation: DeviceValidation = getDeviceValidation(udmiEvent, deviceKey);
      if (this.deviceValidationPgDao) await this.deviceValidationPgDao.insert(deviceValidation);
    }
  }
}
