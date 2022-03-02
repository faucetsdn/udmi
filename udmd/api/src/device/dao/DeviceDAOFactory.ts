import { Configuration } from '../../server/config';
import { DeviceDAO } from './DeviceDAO';
import { FirestoreDeviceDAO } from './FirestoreDeviceDAO';
import { StaticDeviceDAO } from './StaticDeviceDAO';

export function getDeviceDAO(config: Configuration): DeviceDAO {
  if (config.database === 'FIRESTORE') {
    return new FirestoreDeviceDAO();
  } else {
    return new StaticDeviceDAO();
  }
}
