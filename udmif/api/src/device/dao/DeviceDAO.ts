import {
  Device,
  DeviceMakesSearchOptions,
  DeviceModelsSearchOptions,
  DeviceNamesSearchOptions,
  Point,
  SearchOptions,
  SectionsSearchOptions,
  SitesSearchOptions,
} from '../model';

export interface DeviceDAO {
  getFilteredDeviceCount(searchOptions: SearchOptions): Promise<number>;
  getDevices(searchOptions: SearchOptions): Promise<Device[]>;
  getDeviceCount(): Promise<number>;
  getDevice(id: string): Promise<Device | null>;
  getPoints(deviceId: string): Promise<Point[]>;
  getDeviceNames(searchOptions: DeviceNamesSearchOptions): Promise<String[]>;
  getDeviceMakes(searchOptions: DeviceMakesSearchOptions): Promise<String[]>;
  getDeviceModels(searchOptions: DeviceModelsSearchOptions): Promise<String[]>;
  getSites(searchOptions: SitesSearchOptions): Promise<String[]>;
  getSections(searchOptions: SectionsSearchOptions): Promise<String[]>;
}
