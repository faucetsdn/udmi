import { SearchOptions, Device, Point } from '../../model';
import { DeviceDAO } from '../DeviceDAO';
import { Db, Filter } from 'mongodb';
import { fromString } from '../../../device/FilterParser';
import { getFilter } from './MongoFilterBuilder';
import { getSort } from './MongoSortBuilder';

// this class exists to return sorted, and filtered data from MongoDB
export class MongoDeviceDAO implements DeviceDAO {
  constructor(private db: Db) {}

  async getDevices(searchOptions: SearchOptions): Promise<Device[]> {
    return this.db
      .collection<Device>('device')
      .find(this.getFilter(searchOptions))
      .sort(this.getSort(searchOptions))
      .skip(searchOptions.offset)
      .limit(searchOptions.batchSize)
      .toArray();
  }

  async getDevice(id: string): Promise<Device | null> {
    return this.db.collection<Device>('device').findOne({ id });
  }

  async getFilteredDeviceCount(searchOptions: SearchOptions): Promise<number> {
    return this.db.collection<Device>('device').countDocuments(this.getFilter(searchOptions));
  }

  async getDeviceCount(): Promise<number> {
    return this.db.collection<Device>('device').countDocuments();
  }

  async getPoints(deviceId: string): Promise<Point[]> {
    const device: Device = await this.getDevice(deviceId);
    return device ? device.points : [];
  }

  private getFilter(searchOptions: SearchOptions): Filter<Device> {
    return searchOptions.filter ? getFilter(fromString(searchOptions.filter)) : {};
  }

  private getSort(searchOptions: SearchOptions): any {
    return searchOptions.sortOptions ? getSort(searchOptions.sortOptions) : {};
  }
}
