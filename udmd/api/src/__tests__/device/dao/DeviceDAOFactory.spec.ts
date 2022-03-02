import { Configuration, loadConfig } from '../../../server/config';
import { getDeviceDAO } from '../../../device/dao/DeviceDAOFactory';
import { DeviceDAO } from '../../../device/dao/DeviceDAO';
import { StaticDeviceDAO } from '../../../device/dao/StaticDeviceDAO';
import { FirestoreDeviceDAO } from '../../../device/dao/FirestoreDeviceDAO';

describe('DeviceDAOFactory.getDeviceDAO', () => {
  test('returns a static device dao', () => {
    process.env.DATABASE = 'STATIC';
    expect(getDeviceDAO(loadConfig())).toBeInstanceOf(StaticDeviceDAO);
  });

  test('returns a firestore device dao', () => {
    process.env.DATABASE = 'FIRESTORE';
    expect(getDeviceDAO(loadConfig())).toBeInstanceOf(FirestoreDeviceDAO);
  });
});
