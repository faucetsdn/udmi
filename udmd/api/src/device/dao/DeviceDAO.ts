import { Device, SearchOptions } from '../model';

export interface DeviceDAO {
  getFilteredDeviceCount(searchOptions: SearchOptions): Promise<number>;
  getDevices(searchOptions: SearchOptions): Promise<Device[]>;
  getDeviceCount(): Promise<number>;
  getDevice(id: string): Promise<Device | null>;
}
