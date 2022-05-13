import { Collection } from 'mongodb';
import { DeviceKey, DeviceDocument } from './model';

export interface DeviceDao {
  upsert(filterQuery: any, updateQuery: any);
}

export class DefaultDeviceDao implements DeviceDao {
  constructor(private collection: Collection) {}

  async upsert(deviceKey: DeviceKey, deviceDocument: DeviceDocument): Promise<void> {
    // we're using upsert which will allow document updates if it already exists and a document cretion if it does not
    console.debug('Attempting to write the following device document: ' + JSON.stringify(deviceDocument));
    const options = { upsert: true };
    const result = await this.collection.updateOne(deviceKey, { $set: deviceDocument }, options);
    // let's log the result to give us some feedback on what occurred during the upsert
    console.log(JSON.stringify(result));
  }
}
