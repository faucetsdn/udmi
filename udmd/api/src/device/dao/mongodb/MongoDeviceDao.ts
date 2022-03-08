import { SearchOptions, Device } from '../../model';
import { DeviceDAO } from '../DeviceDAO';
import { Db } from 'mongodb';

// this class exists to return sorted, and filtered data from MongoDB
export class MongoDeviceDAO implements DeviceDAO {
  constructor(private db: Db) {}

  async getDevices(searchOptions: SearchOptions): Promise<Device[]> {
    return this.db.collection<Device>('device').find().limit(searchOptions.batchSize).toArray();
  }

  async getDeviceCount(): Promise<number> {
    return this.db.collection<Device>('device').countDocuments();
  }
}
