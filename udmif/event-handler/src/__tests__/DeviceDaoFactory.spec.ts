import { getDeviceDAO, getUri } from '../DeviceDaoFactory';
import { MongoClient } from 'mongodb';

const mockClient = jest.fn().mockImplementation(() => {
  return {
    db: () => {
      return { collection: jest.fn() };
    },
  };
});

beforeEach(() => {
  jest.resetModules();
  jest.clearAllMocks();
  //mock the static MongoClient.connect here
  MongoClient.connect = mockClient;
});

describe('DeviceDaoFactory.getDeviceDAO()', () => {
  test('returns a DeviceDao object', async () => {
    await expect(getDeviceDAO()).toBeTruthy();
  });
});

describe('DeviceDaoFactory.getUri()', () => {

  // take a backup of the environment prior to running the tests
  const ENV_BACKUP = { ...process.env };

  afterEach(() => {
    process.env = { ...ENV_BACKUP }; // Restore old environment
  });

  test('returns a uri with a host', async () => {
    process.env.MONGO_HOST = 'host:8001';
    await expect(getUri()).toEqual('undefined://host:8001');
  });
  test('returns a uri with a protocol', async () => {
    process.env.MONGO_PROTOCOL = 'mongodb';
    await expect(getUri()).toEqual('mongodb://undefined');
  });
  test('returns a uri with a user and password', async () => {
    process.env.MONGO_PROTOCOL = 'mongodb';
    process.env.MONGO_HOST = 'host:8001';
    process.env.MONGO_USER = 'user';
    process.env.MONGO_PWD = 'pwd';
    await expect(getUri()).toEqual('mongodb://user:pwd@host:8001');
  });
});
