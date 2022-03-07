import { cli } from 'winston/lib/winston/config';
import { SearchOptions, Device } from '../../model';
import { DeviceDAO } from '../DeviceDAO';
import { getMongoDb } from './MongoClient';
import { Db } from 'mongodb';

// this class exists to return static, sorted, and filtered data
export class MongoDeviceDAO implements DeviceDAO {
  constructor(private db: Db) {}

  async getDevices(searchOptions: SearchOptions): Promise<Device[]> {
    const devices = await this.db.collection('device').find();

    return [];
  }
  async getDeviceCount(): Promise<number> {
    return 0;
  }
}
