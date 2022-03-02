import { Device, SearchOptions } from '../model';

export interface DeviceDAO {
  getDevices(searchOptions: SearchOptions): Promise<Device[]>;
  getDeviceCount(): Promise<number>;
}
