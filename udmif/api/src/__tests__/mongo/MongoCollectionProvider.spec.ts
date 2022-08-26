import { MongoClient } from 'mongodb';
import { getMongoCollection, getMongoDb } from '../../mongo/MongoCollectionProvider';
import { loadConfig } from '../../server/config';

describe('MongoCollectionProvider', () => {
  let client: MongoClient;

  const config = loadConfig();
  const collection = jest.fn();
  const db = jest.fn().mockReturnValue({ collection });
  const mockClient = jest.fn();

  beforeAll(async () => {
    client = await MongoClient.connect(process.env.MONGO_URL);
  });

  beforeEach(() => {
    mockClient.mockReturnValue({
      db,
    });

    MongoClient.connect = mockClient;
  });

  afterAll(async () => {
    await client.close();
  });

  describe('getMongoCollection', () => {
    test('returns a collection of the type specified', async () => {
      await getMongoCollection('my-collection', config); // test with Device type

      expect(collection).toHaveBeenCalledWith('my-collection');
    });
  });

  describe('getMongoDb', () => {
    test('returns the db', async () => {
      jest.spyOn(client, 'db');

      await getMongoDb({ ...config, mongoDatabase: 'tmp' });

      expect(db).toHaveBeenCalledWith('tmp');
    });
  });
});
