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
  SitesSearchOptions,
} from './model';
import { DeviceDAO } from './dao/DeviceDAO';
import {
  validate,
  validateDeviceMakesSearchOptions,
  validateDeviceModelsSearchOptions,
  validateDeviceNamesSearchOptions,
  validateSectionsSearchOptions,
  validateSitesSearchOptions,
} from './SearchOptionsValidator';

export class DeviceDataSource extends GraphQLDataSource {
  constructor(private deviceDAO: DeviceDAO) {
    super();
  }

  public initialize(config): void {
    super.initialize(config);
  }

  async getDevices(searchOptions: SearchOptions): Promise<DevicesResponse> {
    const validatedSearchOptions: SearchOptions = validate(searchOptions);

    const devices: Device[] = await this.deviceDAO.getDevices(validatedSearchOptions);
    const totalCount = await this.deviceDAO.getDeviceCount();
    const totalFilteredCount: number = await this.deviceDAO.getFilteredDeviceCount(validatedSearchOptions);

    return { devices, totalCount, totalFilteredCount };
  }

  async getDevice(id: string): Promise<Device> {
    return this.deviceDAO.getDevice(id);
  }

  async getPoints(deviceId: string): Promise<Point[]> {
    return this.deviceDAO.getPoints(deviceId);
  }

  async getDeviceNames(searchOptions: DeviceNamesSearchOptions): Promise<String[]> {
    const validatedSearchOptions: DeviceNamesSearchOptions = validateDeviceNamesSearchOptions(searchOptions);
    return this.deviceDAO.getDeviceNames(validatedSearchOptions);
  }

  async getDeviceMakes(searchOptions: DeviceMakesSearchOptions): Promise<String[]> {
    const validatedSearchOptions: DeviceMakesSearchOptions = validateDeviceMakesSearchOptions(searchOptions);
    return this.deviceDAO.getDeviceMakes(validatedSearchOptions);
  }

  async getDeviceModels(searchOptions: DeviceModelsSearchOptions): Promise<String[]> {
    const validatedSearchOptions: DeviceModelsSearchOptions = validateDeviceModelsSearchOptions(searchOptions);
    return this.deviceDAO.getDeviceModels(validatedSearchOptions);
  }

  async getSites(searchOptions: SitesSearchOptions): Promise<String[]> {
    const validatedSearchOptions: SitesSearchOptions = validateSitesSearchOptions(searchOptions);
    return this.deviceDAO.getSites(validatedSearchOptions);
  }

  async getSections(searchOptions: SectionsSearchOptions): Promise<String[]> {
    const validatedSearchOptions: SectionsSearchOptions = validateSectionsSearchOptions(searchOptions);
    return this.deviceDAO.getSections(validatedSearchOptions);
  }
}
