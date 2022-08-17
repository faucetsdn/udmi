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
  Site,
  SitesResponse,
  SiteNamesSearchOptions,
  ValidatedDeviceMakesSearchOptions,
  ValidatedDeviceModelsSearchOptions,
  ValidatedDeviceNamesSearchOptions,
  ValidatedSectionsSearchOptions,
  ValidatedSiteNamesSearchOptions,
} from './model';
import { DeviceDAO } from './dao/DeviceDAO';
import {
  validate,
  validateDeviceMakesSearchOptions,
  validateDeviceModelsSearchOptions,
  validateDeviceNamesSearchOptions,
  validateSectionsSearchOptions,
  validateSiteNamesSearchOptions,
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

  async getDeviceNames(searchOptions?: DeviceNamesSearchOptions): Promise<string[]> {
    const validatedSearchOptions: ValidatedDeviceNamesSearchOptions = validateDeviceNamesSearchOptions(searchOptions);
    return this.deviceDAO.getDeviceNames(validatedSearchOptions);
  }

  async getDeviceMakes(searchOptions?: DeviceMakesSearchOptions): Promise<string[]> {
    const validatedSearchOptions: ValidatedDeviceMakesSearchOptions = validateDeviceMakesSearchOptions(searchOptions);
    return this.deviceDAO.getDeviceMakes(validatedSearchOptions);
  }

  async getDeviceModels(searchOptions?: DeviceModelsSearchOptions): Promise<string[]> {
    const validatedSearchOptions: ValidatedDeviceModelsSearchOptions = validateDeviceModelsSearchOptions(searchOptions);
    return this.deviceDAO.getDeviceModels(validatedSearchOptions);
  }

  async getSiteNames(searchOptions?: SiteNamesSearchOptions): Promise<string[]> {
    const validatedSearchOptions: ValidatedSiteNamesSearchOptions = validateSiteNamesSearchOptions(searchOptions);
    return this.deviceDAO.getSiteNames(validatedSearchOptions);
  }

  async getSections(searchOptions?: SectionsSearchOptions): Promise<string[]> {
    const validatedSearchOptions: ValidatedSectionsSearchOptions = validateSectionsSearchOptions(searchOptions);
    return this.deviceDAO.getSections(validatedSearchOptions);
  }

  async getSites(searchOptions: SearchOptions): Promise<SitesResponse> {
    const validatedSearchOptions: SearchOptions = validate(searchOptions);

    const sites: Site[] = await this.deviceDAO.getSites(validatedSearchOptions);
    const totalCount = await this.deviceDAO.getSiteCount();
    const totalFilteredCount: number = await this.deviceDAO.getFilteredSiteCount(validatedSearchOptions);

    return { sites, totalCount, totalFilteredCount };
  }
}
