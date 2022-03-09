import { SearchOptions, Device } from '../../model';
import { DeviceDAO } from '../DeviceDAO';
import { Db } from 'mongodb';
import { fromString } from '../../../device/FilterParser';
import { getFilter } from './MongoFilterBuilder';
import { logger } from '../../../common/logger';

// this class exists to return sorted, and filtered data from MongoDB
export class MongoDeviceDAO implements DeviceDAO {
  constructor(private db: Db) {}

  async getDevices(searchOptions: SearchOptions): Promise<Device[]> {
    return this.db
      .collection<Device>('device')
      .find(this.getFilter(searchOptions))
      .sort(this.getSort(searchOptions))
      .skip(this.getOffset(searchOptions))
      .limit(this.getBatchSize(searchOptions))
      .toArray();
  }

  async getDeviceCount(): Promise<number> {
    return this.db.collection<Device>('device').countDocuments();
  }

  private getFilter(searchOptions: SearchOptions): any {
    return searchOptions.filter ? getFilter(fromString(searchOptions.filter)) : {};
  }

  private getSort(searchOptions: SearchOptions): any {
    return searchOptions.filter ? {} : {};
  }

  private getOffset(searchOptions: SearchOptions): number {
    const offset = searchOptions.offset;
    if (offset === undefined) {
      logger.warn(`An offset was not provided, defaulting to 0`);
      return 0;
    }

    return offset;
  }

  private getBatchSize(searchOptions: SearchOptions): number {
    const batchSize: number = searchOptions.batchSize;
    if (batchSize > 1000) {
      logger.warn(`The batch size ${batchSize} exceeds max of 1000, restricting to 1000 records`);
      return 1000;
    }

    return batchSize;
  }
}
