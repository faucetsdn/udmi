import { Collection } from 'mongodb';
import { DeviceDocument } from './DeviceDocument';
import { DeviceKey } from './DeviceKey';

const options = { upsert: true };

export interface DeviceDao {
  upsert(filterQuery: any, updateQuery: any);
  get(filterQuery: any);
}

export class DefaultDeviceDao implements DeviceDao {
  constructor(private collection: Collection<DeviceDocument>) {}

  /**
   * Updates a device document if it is found using the device key, else it will insert a new device document
   * @param {DeviceKey} deviceKey
   * @param {DeviceDocument} deviceDocument
   */
  async upsert(deviceKey: DeviceKey, deviceDocument: DeviceDocument): Promise<void> {
    // we're using upsert which will allow document updates if it already exists and a document cretion if it does not
    console.log('Attempting to write the following device document: ' + JSON.stringify(deviceDocument));

    const result = await this.collection.updateOne(deviceKey, { $set: deviceDocument }, options);

    // let's log the result to give us some feedback on what occurred during the upsert
    console.log(JSON.stringify(result));
  }

  /**
   * Gets a single device document using the device key
   * @param {DeviceKey} deviceKey
   * @returns {DeviceDocument}
   */
  async get(deviceKey: DeviceKey): Promise<DeviceDocument> {
    return await this.collection.findOne(deviceKey);
  }
}
