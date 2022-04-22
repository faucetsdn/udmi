import { MongoClient } from 'mongodb';
import { getMongoDb } from '../../../../device/dao/mongodb/MongoClient';
import { Configuration } from '../../../../server/config';

const mockDb = jest.fn();

const mockClient = jest.fn().mockImplementation(() => {
  return { db: mockDb };
});

const mongoProtocol: string = 'mongodb';
const mongoHost: string = 'some-host';

const uri: string = `${mongoProtocol}://${mongoHost}`;
const defaultOptions = { minPoolSize: 1, maxPoolSize: 10 };

describe('getMongoDb', () => {
  var config: Configuration;

  beforeEach(() => {
    jest.clearAllMocks();
    //mock the static MongoClient.connect here
    MongoClient.connect = mockClient;

    config = {
      nodeEnv: '',
      datasource: '',
      projectId: '',
      logLevel: '',
      mongoUsername: '',
      mongoPassword: '',
      mongoProtocol,
      mongoHost,
      mongoDatabase: '',
      authClientId: '',
      clientIds: [''],
    };
  });

  test('getMongoDb with default client options', async () => {
    await getMongoDb(config);
    expect(mockClient).toHaveBeenNthCalledWith(1, uri, defaultOptions);
  });

  test('getMongoDb with MongoConfig that has user information', async () => {
    config.mongoUsername = 'test-user';
    config.mongoPassword = 'test-pwd';
    const uriWithCreds = `${mongoProtocol}://${config.mongoUsername}:${config.mongoPassword}@${mongoHost}`;

    await getMongoDb(config);
    expect(mockClient).toHaveBeenNthCalledWith(1, uriWithCreds, defaultOptions);
  });

  test('getMongoDb throws error if an exception occurs getting the db', async () => {
    mockDb.mockRejectedValueOnce(new Error('some-error'));

    await expect(getMongoDb(config)).rejects.toThrow('some-error');
  });
});
