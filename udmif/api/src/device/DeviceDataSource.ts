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
import { validate } from './SearchOptionsValidator';

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
    return this.deviceDAO.getDeviceNames(searchOptions);
  }

  async getDeviceMakes(searchOptions: DeviceMakesSearchOptions): Promise<String[]> {
    return this.deviceDAO.getDeviceMakes(searchOptions);
  }

  async getDeviceModels(searchOptions: DeviceModelsSearchOptions): Promise<String[]> {
    return this.deviceDAO.getDeviceModels(searchOptions);
  }

  async getSites(searchOptions: SitesSearchOptions): Promise<String[]> {
    return this.deviceDAO.getSites(searchOptions);
  }

  async getSections(searchOptions: SectionsSearchOptions): Promise<String[]> {
    return this.deviceDAO.getSections(searchOptions);
  }
}
