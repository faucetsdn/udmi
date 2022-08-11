import { Collection } from 'mongodb';
import { Device } from './model/Device';
import { DeviceKey } from './model/DeviceKey';

const options = { upsert: true };

export interface DeviceDao {
  upsert(filterQuery: any, updateQuery: any);
  get(filterQuery: any);
}

export class DefaultDeviceDao implements DeviceDao {
  constructor(private collection: Collection<Device>) { }

  /**
   * Updates a device document if it is found using the device key, else it will insert a new device document
   * @param {DeviceKey} deviceKey
   * @param {Device} deviceDocument 
   */
  async upsert(deviceKey: DeviceKey, deviceDocument: Device): Promise<void> {
    // we're using upsert which will allow document updates if it already exists and a document cretion if it does not
    console.log('Attempting to upsert the following device document: ' + JSON.stringify(deviceDocument));

    const result = await this.collection.updateOne(deviceKey, { $set: deviceDocument }, options);

    // let's log the result to give us some feedback on what occurred during the upsert
    console.log('Upsert result:  ' + JSON.stringify(result));
  }

  /**
   * Gets a single device document using the device key
   * @param {DeviceKey} deviceKey
   * @returns {Device}
   */
  async get(deviceKey: DeviceKey): Promise<Device> {
    return this.collection.findOne(deviceKey);
  }
}
