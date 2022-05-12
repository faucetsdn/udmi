import {
  SearchOptions,
  Device,
  Point,
  DeviceNamesSearchOptions,
  DeviceMakesSearchOptions,
  DeviceModelsSearchOptions,
  SitesSearchOptions,
  SectionsSearchOptions,
} from '../../model';
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

  async getDeviceNames(searchOptions: DeviceNamesSearchOptions): Promise<String[]> {
    return this.getDistinct('name', searchOptions.search, searchOptions.limit);
  }

  async getDeviceMakes(searchOptions: DeviceMakesSearchOptions): Promise<String[]> {
    return this.getDistinct('make', searchOptions.search, searchOptions.limit);
  }

  async getDeviceModels(searchOptions: DeviceModelsSearchOptions): Promise<String[]> {
    return this.getDistinct('model', searchOptions.search, searchOptions.limit);
  }

  async getSites(searchOptions: SitesSearchOptions): Promise<String[]> {
    return this.getDistinct('site', searchOptions.search, searchOptions.limit);
  }

  async getSections(searchOptions: SectionsSearchOptions): Promise<String[]> {
    return this.getDistinct('section', searchOptions.search, searchOptions.limit);
  }

  private getFilter(searchOptions: SearchOptions): Filter<Device> {
    return searchOptions.filter ? getFilter(fromString(searchOptions.filter)) : {};
  }

  private getSort(searchOptions: SearchOptions): any {
    return searchOptions.sortOptions ? getSort(searchOptions.sortOptions) : { _id: 1 };
  }

  private async getDistinct(field: string, search?: string, limit?: number): Promise<string[]> {
    try {
      return this.db
        .collection<Device>('device')
        .aggregate([
          { $match: { [field]: { $in: [new RegExp(search, 'i')] } } },
          { $group: { _id: `$${field}`, distinct_doc: { $first: '$$ROOT' } } },
          {
            $replaceRoot: {
              newRoot: '$distinct_doc',
            },
          },
          { $limit: limit },
          { $sort: { [field]: 1 } },
        ])
        .map((device: Device) => device[field])
        .toArray();
    } catch {
      return [];
    }
  }
}
