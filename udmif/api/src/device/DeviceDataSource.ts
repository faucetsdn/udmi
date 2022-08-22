import { GraphQLDataSource } from 'apollo-datasource-graphql/dist/GraphQLDataSource';
import {
  Device,
  DeviceMakesSearchOptions,
  DeviceModelsSearchOptions,
  DeviceNamesSearchOptions,
  DevicesResponse,
  Point,
  SearchOptions,
  SectionsSearchOptions,
  ValidatedDeviceMakesSearchOptions,
  ValidatedDeviceModelsSearchOptions,
  ValidatedDeviceNamesSearchOptions,
  ValidatedSectionsSearchOptions,
} from './model';
import {
  validate,
  validateDeviceMakesSearchOptions,
  validateDeviceModelsSearchOptions,
  validateDeviceNamesSearchOptions,
  validateSectionsSearchOptions,
} from './SearchOptionsValidator';
import { DAO } from '../dao/DAO';

export class DeviceDataSource extends GraphQLDataSource {
  constructor(private deviceDAO: DAO<Device>) {
    super();
  }

  public initialize(config): void {
    super.initialize(config);
  }

  async getDevices(searchOptions: SearchOptions): Promise<DevicesResponse> {
    const validatedSearchOptions: SearchOptions = validate(searchOptions);

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

  async getDeviceNames(searchOptions?: DeviceNamesSearchOptions): Promise<string[]> {
    const validatedSearchOptions: ValidatedDeviceNamesSearchOptions = validateDeviceNamesSearchOptions(searchOptions);
    return this.deviceDAO.getDistinct('name', validatedSearchOptions);
  }

  async getDeviceMakes(searchOptions?: DeviceMakesSearchOptions): Promise<string[]> {
    const validatedSearchOptions: ValidatedDeviceMakesSearchOptions = validateDeviceMakesSearchOptions(searchOptions);
    return this.deviceDAO.getDistinct('make', validatedSearchOptions);
  }

  async getDeviceModels(searchOptions?: DeviceModelsSearchOptions): Promise<string[]> {
    const validatedSearchOptions: ValidatedDeviceModelsSearchOptions = validateDeviceModelsSearchOptions(searchOptions);
    return this.deviceDAO.getDistinct('model', validatedSearchOptions);
  }

  async getSections(searchOptions?: SectionsSearchOptions): Promise<string[]> {
    const validatedSearchOptions: ValidatedSectionsSearchOptions = validateSectionsSearchOptions(searchOptions);
    return this.deviceDAO.getDistinct('section', validatedSearchOptions);
  }
}
