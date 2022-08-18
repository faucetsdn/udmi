import { getUri } from '../../dao/MongoCollectionProvider';
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

describe('MongoCollectionFactory.getUri()', () => {
  // take a backup of the environment prior to running the tests
  const ENV_BACKUP = { ...process.env };

  afterEach(() => {
    process.env = { ...ENV_BACKUP }; // Restore old environment
  });

  test('returns a uri with a host', () => {
    process.env.MONGO_HOST = 'host:8001';
    expect(getUri()).toEqual('undefined://host:8001');
  });
  test('returns a uri with a protocol', () => {
    process.env.MONGO_PROTOCOL = 'mongodb';
    expect(getUri()).toEqual('mongodb://undefined');
  });
  test('returns a uri with a user and password', () => {
    process.env.MONGO_PROTOCOL = 'mongodb';
    process.env.MONGO_HOST = 'host:8001';
    process.env.MONGO_USER = 'user';
    process.env.MONGO_PWD = 'pwd';
    expect(getUri()).toEqual('mongodb://user:pwd@host:8001');
  });
});
