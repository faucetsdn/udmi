import { loadConfig } from '../../../server/config';
import { getDeviceDAO } from '../../../device/dao/DeviceDAOFactory';
import { StaticDeviceDAO } from '../../../device/dao/static/StaticDeviceDAO';
import { MongoDeviceDAO } from '../../../device/dao/mongodb/MongoDeviceDAO';
import { MongoClient } from 'mongodb';

const mockClient = jest.fn().mockImplementation(() => {
  return { db: jest.fn() };
});

describe('DeviceDAOFactory.getDeviceDAO', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    //mock the static MongoClient.connect here
    MongoClient.connect = mockClient;
  });

  test('returns a static device dao', async () => {
    const config = loadConfig();
    config.datasource = 'STATIC';
    await expect(getDeviceDAO(config)).resolves.toBeInstanceOf(StaticDeviceDAO);
  });

  test('returns a mongo device dao', async () => {
    const config = loadConfig();
    config.datasource = 'MONGO';
    await expect(getDeviceDAO(config)).resolves.toBeInstanceOf(MongoDeviceDAO);
  });
});
