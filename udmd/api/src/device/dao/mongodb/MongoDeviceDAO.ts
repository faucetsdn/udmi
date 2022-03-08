import { SearchOptions, Device, SortOptions, Filter } from '../../model';
import { DeviceDAO } from '../DeviceDAO';
import { Db } from 'mongodb';
import { fromString } from '../../../device/FilterParser';
import { getFilter } from './MongoFilterBuilder';

// this class exists to return sorted, and filtered data from MongoDB
export class MongoDeviceDAO implements DeviceDAO {
  constructor(private db: Db) {}

  async getDevices(searchOptions: SearchOptions): Promise<Device[]> {
    console.log(searchOptions);

    const sortOptions: SortOptions = searchOptions.sortOptions;

    let sortField: string;
    if (sortOptions) {
      console.log(sortOptions);
      sortField = sortOptions.field;
    }

    let filter;
    if (searchOptions.filter) {
      let filters: Filter[] = fromString(searchOptions.filter);
      filter = getFilter(filters);
    }

    return this.db
      .collection<Device>('device')
      .find(filter)
      .skip(searchOptions.offset)
      .limit(searchOptions.batchSize)
      .toArray();
  }

  async getDeviceCount(): Promise<number> {
    return this.db.collection<Device>('device').countDocuments();
  }
}
