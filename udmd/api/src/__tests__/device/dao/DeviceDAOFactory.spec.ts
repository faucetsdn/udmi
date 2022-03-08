import { loadConfig } from '../../../server/config';
import { getDeviceDAO } from '../../../device/dao/DeviceDAOFactory';
import { StaticDeviceDAO } from '../../../device/dao/StaticDeviceDAO';
import { MongoDeviceDAO } from '../../../device/dao/mongodb/MongoDeviceDao';

describe('DeviceDAOFactory.getDeviceDAO', () => {
  test('returns a static device dao', async () => {
    process.env.DATABASE = 'STATIC';
    await expect(getDeviceDAO(loadConfig())).resolves.toBeInstanceOf(StaticDeviceDAO);
  });

  test('returns a firestore device dao', async () => {
    process.env.DATABASE = 'MONGO';
    await expect(getDeviceDAO(loadConfig())).resolves.toBeInstanceOf(MongoDeviceDAO);
  });
});
