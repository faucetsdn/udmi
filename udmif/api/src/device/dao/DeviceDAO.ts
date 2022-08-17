import {
  Device,
  Point,
  SearchOptions,
  Site,
  ValidatedDeviceMakesSearchOptions,
  ValidatedDeviceModelsSearchOptions,
  ValidatedDeviceNamesSearchOptions,
  ValidatedSectionsSearchOptions,
  ValidatedSiteNamesSearchOptions,
} from '../model';

export interface DeviceDAO {
  getFilteredDeviceCount(searchOptions: SearchOptions): Promise<number>;
  getDevices(searchOptions: SearchOptions): Promise<Device[]>;
  getDeviceCount(): Promise<number>;
  getDevice(id: string): Promise<Device | null>;
  getPoints(deviceId: string): Promise<Point[]>;
  getDeviceNames(searchOptions: ValidatedDeviceNamesSearchOptions): Promise<string[]>;
  getDeviceMakes(searchOptions: ValidatedDeviceMakesSearchOptions): Promise<string[]>;
  getDeviceModels(searchOptions: ValidatedDeviceModelsSearchOptions): Promise<string[]>;
  getSiteNames(searchOptions: ValidatedSiteNamesSearchOptions): Promise<string[]>;
  getSections(searchOptions: ValidatedSectionsSearchOptions): Promise<string[]>;
  getSiteCount(): Promise<number>;
  getFilteredSiteCount(searchOptions: SearchOptions): Promise<number>;
  getSites(searchOptions: SearchOptions): Promise<Site[]>;
}
