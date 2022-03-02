import { SearchOptions, Device } from '../model';
import { DeviceDAO } from './DeviceDAO';

// this class allows interactions with a firestore db
export class FirestoreDeviceDAO implements DeviceDAO {
  constructor() {}
  getDevices(searchOptions: SearchOptions): Promise<Device[]> {
    throw new Error('Method not implemented.');
  }
  getDeviceCount(): Promise<number> {
    throw new Error('Method not implemented.');
  }
}
