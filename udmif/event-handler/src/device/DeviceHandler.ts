import { createDevice, getDeviceKey, getDeviceValidation } from './DeviceDocumentUtils';
import { Handler } from '../Handler';
import { Device, DeviceKey, DeviceValidation, PRIMARY_KEYS } from './model/Device';
import { UdmiEvent } from '../model/UdmiEvent';
import { DAO } from '../dao/DAO';
import { isValidationSubType } from '../EventUtils';

export class DeviceHandler implements Handler {
  constructor(
    private deviceDao: DAO<Device>,
    private devicePgDao: DAO<Device>,
    private deviceValidationDao: DAO<DeviceValidation>,
    private deviceValidationPgDao: DAO<DeviceValidation>
  ) {}

  async handle(udmiEvent: UdmiEvent): Promise<void> {
    await this.handleMongo(udmiEvent);
    await this.handlePg(udmiEvent);
  }

  async handlePg(udmiEvent: UdmiEvent): Promise<void> {
    if (!this.devicePgDao) {
      return;
    }

    const deviceKey: DeviceKey = getDeviceKey(udmiEvent);

    const existingDeviceDocumentFromPg: Device = await this.devicePgDao.get(deviceKey);

    const pgDevice: Device = createDevice(udmiEvent, existingDeviceDocumentFromPg?.points);

    // let's upsert the existing document
    await this.devicePgDao.upsert(pgDevice, PRIMARY_KEYS);

    // if this is a validation message, we will record the validation message
    if (isValidationSubType(udmiEvent)) {
      const deviceValidation: DeviceValidation = getDeviceValidation(udmiEvent, deviceKey);
      if (this.deviceValidationPgDao) await this.deviceValidationPgDao.insert(deviceValidation);
    }
  }

  async handleMongo(udmiEvent: UdmiEvent): Promise<void> {
    const deviceKey: DeviceKey = getDeviceKey(udmiEvent);

    const existingDeviceDocument: Device = await this.deviceDao.get(deviceKey);

    const mongoDevice: Device = createDevice(udmiEvent, existingDeviceDocument?.points);

    // let's upsert the existing document
    await this.deviceDao.upsert(mongoDevice, deviceKey);

    // if this is a validation message, we will record the validation message
    if (isValidationSubType(udmiEvent)) {
      const deviceValidation: DeviceValidation = getDeviceValidation(udmiEvent, deviceKey);
      await this.deviceValidationDao.insert(deviceValidation);
    }
  }
}
