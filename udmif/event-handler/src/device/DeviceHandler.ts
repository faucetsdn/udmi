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
    const deviceKey: DeviceKey = getDeviceKey(udmiEvent);

    const existingDeviceDocument: Device = await this.deviceDao.get(deviceKey);
    const existingDeviceDocumentFromPg: Device = await this.devicePgDao.get(deviceKey);

    const mongoDevice: Device = createDevice(udmiEvent, existingDeviceDocument?.points);
    const pgDevice: Device = createDevice(udmiEvent, existingDeviceDocumentFromPg?.points);

    // let's upsert the existing document
    await this.deviceDao.upsert(mongoDevice, deviceKey);
    await this.devicePgDao.upsert(pgDevice, PRIMARY_KEYS);

    // if this is a validation message, we will record the validation message
    if (isValidationSubType(udmiEvent)) {
      const deviceValidation: DeviceValidation = getDeviceValidation(udmiEvent, deviceKey);
      await this.deviceValidationDao.insert(deviceValidation);
      await this.deviceValidationPgDao.insert(deviceValidation);
    }

    this.compareObjects(await this.deviceDao.get(deviceKey), await this.devicePgDao.get(deviceKey));
  }

  compareObjects(mongoObject, pgObject) {
    if (!mongoObject || !pgObject) {
      return;
    }

    const sortedMongoObject = JSON.stringify(this.sortObject(mongoObject));
    const sortedPGObject = JSON.stringify(this.sortObject(pgObject));

    sortedMongoObject === sortedPGObject ? console.log('Objects are the same') : console.log('Objects are different');

    console.log('Mongo object: \n' + sortedMongoObject);
    console.log('PG object: \n' + sortedPGObject);
  }

  sortObject(object) {
    return Object.keys(object)
      .sort()
      .reduce((obj, key) => {
        obj[key] = object[key];
        return obj;
      }, {});
  }
}
