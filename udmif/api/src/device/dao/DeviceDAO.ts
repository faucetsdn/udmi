import {
  Device,
  Point,
  SearchOptions,
  ValidatedDeviceMakesSearchOptions,
  ValidatedDeviceModelsSearchOptions,
  ValidatedDeviceNamesSearchOptions,
  ValidatedSectionsSearchOptions,
  ValidatedSitesSearchOptions,
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
  getSites(searchOptions: ValidatedSitesSearchOptions): Promise<string[]>;
  getSections(searchOptions: ValidatedSectionsSearchOptions): Promise<string[]>;
}
