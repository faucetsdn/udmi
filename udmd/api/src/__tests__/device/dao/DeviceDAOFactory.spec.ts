import { Configuration, loadConfig } from '../../../server/config';
import { getDeviceDAO } from '../../../device/dao/DeviceDAOFactory';
import { DeviceDAO } from '../../../device/dao/DeviceDAO';
import { StaticDeviceDAO } from '../../../device/dao/StaticDeviceDAO';
import { FirestoreDeviceDAO } from '../../../device/dao/FirestoreDeviceDAO';

describe('DeviceDAOFactory.getDeviceDAO', () => {
  test('returns a static device dao', () => {
    process.env.DATABASE = 'STATIC';
    const deviceDAO: DeviceDAO = getDeviceDAO(loadConfig());
    expect(deviceDAO).toBeInstanceOf(StaticDeviceDAO);
  });

  test('returns a firestore device dao', () => {
    process.env.DATABASE = 'FIRESTORE';
    const deviceDAO: DeviceDAO = getDeviceDAO(loadConfig());
    expect(deviceDAO).toBeInstanceOf(FirestoreDeviceDAO);
  });
});
