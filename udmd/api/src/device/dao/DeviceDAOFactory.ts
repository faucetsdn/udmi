import { Configuration } from '../../server/config';
import { DeviceDAO } from './DeviceDAO';
import { StaticDeviceDAO } from './StaticDeviceDAO';

export function getDeviceDAO(config: Configuration): DeviceDAO {
  return new StaticDeviceDAO();
}
