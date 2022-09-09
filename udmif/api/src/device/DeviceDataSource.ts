import { GraphQLDataSource } from 'apollo-datasource-graphql/dist/GraphQLDataSource';
import { Device, DevicesResponse, Point } from './model';
import { validateSearchOptions, validateDistinctSearchOptions } from '../common/SearchOptionsValidator';
import { DAO } from '../dao/DAO';
import {
  ValidatedDistinctSearchOptions,
  DistinctSearchOptions,
  ValidatedSearchOptions,
  SearchOptions,
} from '../common/model';

export class DeviceDataSource extends GraphQLDataSource {
  constructor(private deviceDAO: DAO<Device>) {
    super();
  }

  public initialize(config): void {
    super.initialize(config);
  }

  async getDevices(searchOptions?: SearchOptions): Promise<DevicesResponse> {
    const validatedSearchOptions: ValidatedSearchOptions = validateSearchOptions(searchOptions);

    const devices: Device[] = await this.deviceDAO.getAll(validatedSearchOptions);
    const totalCount = await this.deviceDAO.getCount();
    const totalFilteredCount: number = await this.deviceDAO.getFilteredCount(validatedSearchOptions);

    return { devices, totalCount, totalFilteredCount };
  }

  async getDevice(id: string): Promise<Device> {
    return this.deviceDAO.getOne({ id });
  }

  async getPoints(deviceId: string): Promise<Point[]> {
    return (await this.getDevice(deviceId))?.points ?? [];
  }

  async getDeviceNames(searchOptions?: DistinctSearchOptions): Promise<string[]> {
    const validatedSearchOptions: ValidatedDistinctSearchOptions = validateDistinctSearchOptions(searchOptions);
    return this.deviceDAO.getDistinct('name', validatedSearchOptions);
  }

  async getDeviceMakes(searchOptions?: DistinctSearchOptions): Promise<string[]> {
    const validatedSearchOptions: ValidatedDistinctSearchOptions = validateDistinctSearchOptions(searchOptions);
    return this.deviceDAO.getDistinct('make', validatedSearchOptions);
  }

  async getDeviceModels(searchOptions?: DistinctSearchOptions): Promise<string[]> {
    const validatedSearchOptions: ValidatedDistinctSearchOptions = validateDistinctSearchOptions(searchOptions);
    return this.deviceDAO.getDistinct('model', validatedSearchOptions);
  }

  async getSections(searchOptions?: DistinctSearchOptions): Promise<string[]> {
    const validatedSearchOptions: ValidatedDistinctSearchOptions = validateDistinctSearchOptions(searchOptions);
    return this.deviceDAO.getDistinct('section', validatedSearchOptions);
  }

  async getSiteDevices(siteName: string): Promise<DevicesResponse> {
    return this.getDevices({
      offset: 0,
      filter: JSON.stringify([
        {
          field: 'site',
          operator: '=',
          value: siteName,
        },
      ]),
    });
  }

  async getSiteDevicesCount(siteName: string): Promise<number> {
    return (await this.getSiteDevices(siteName)).totalFilteredCount;
  }
}
