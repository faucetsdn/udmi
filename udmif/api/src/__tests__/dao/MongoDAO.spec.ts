import { Collection, MongoClient, Db } from 'mongodb';
import { Filter } from '../../common/model';
import { DAO } from '../../dao/DAO';
import { DefaultDAO } from '../../dao/MongoDAO';
import { Device } from '../../device/model';

describe('DAO', () => {
  let deviceDao: DAO<Device>; // test with Device as an example
  let deviceCollection: Collection<Device>;
  let connection: MongoClient;
  let db: Db;

  const mockClient = jest.fn();

  beforeAll(async () => {
    // in memory mongo
    connection = await MongoClient.connect(process.env.MONGO_URL);
    db = connection.db('tmp');
  });

  beforeEach(async () => {
    mockClient.mockReturnValue({
      db: () => {
        return { collection: jest.fn() };
      },
    });

    // mock the static MongoClient.connect here
    MongoClient.connect = mockClient;

    deviceCollection = db.collection('device');
    deviceDao = new DefaultDAO<Device>(deviceCollection);
    await deviceCollection.deleteMany({});
  });

  afterAll(async () => {
    // make sure we close it before the test completely finish
    await connection.close();
  });

  describe('getAll', () => {
    test('return all the documents', async () => {
      const insertedDeviceDocument: Device = { id: 'd-id-1', name: 'device-1', site: 'LOC' };

      deviceCollection.insertOne(insertedDeviceDocument);

      const devides: Device[] = await deviceDao.getAll({ batchSize: 10, offset: 0 });

      expect(devides).toEqual([insertedDeviceDocument]);
    });
  });

  describe('getOne', () => {
    test('return a single document', async () => {
      const insertedDeviceDocument: Device = { id: 'd-id-1', name: 'device-1', site: 'LOC' };

      deviceCollection.insertOne(insertedDeviceDocument);

      const device: Device = await deviceDao.getOne({ id: 'd-id-1' });

      expect(device).toEqual(insertedDeviceDocument);
    });
  });

  describe('getFilteredCount', () => {
    test('return the count of the filtered documents', async () => {
      const insertedDeviceDocument1: Device = { id: 'd-id-1', name: 'device-1', site: 'LOC' };
      const insertedDeviceDocument2: Device = { id: 'd-id-2', name: 'device-2', site: 'LOC' };

      deviceCollection.insertMany([insertedDeviceDocument1, insertedDeviceDocument2]);

      const filteredCount: number = await deviceDao.getFilteredCount({
        batchSize: 10,
        offset: 0,
        filter: JSON.stringify(<Filter[]>[{ field: 'name', operator: '~', value: '2' }]),
      });

      expect(filteredCount).toEqual(1);
    });
  });

  describe('getCount', () => {
    test('return the count of the documents', async () => {
      const insertedDeviceDocument1: Device = { id: 'd-id-1', name: 'device-1', site: 'LOC' };
      const insertedDeviceDocument2: Device = { id: 'd-id-2', name: 'device-2', site: 'LOC' };

      deviceCollection.insertMany([insertedDeviceDocument1, insertedDeviceDocument2]);

      const count: number = await deviceDao.getCount();

      expect(count).toEqual(2);
    });
  });

  describe('getDistinct', () => {
    test('return unique values of a field in a document', async () => {
      const insertedDeviceDocument1: Device = { id: 'd-id-1', name: 'device-1', site: 'LOC' };
      const insertedDeviceDocument2: Device = { id: 'd-id-2', name: 'device-2', site: 'LOC' };

      deviceCollection.insertMany([insertedDeviceDocument1, insertedDeviceDocument2]);

      const deviceNames: string[] = await deviceDao.getDistinct('name', { limit: 10 });

      expect(deviceNames).toEqual(['device-1', 'device-2']);
    });

    test('return unique values of a field in a document with a filter', async () => {
      const insertedDeviceDocument1: Device = { id: 'd-id-1', name: 'device-1', site: 'LOC-A' };
      const insertedDeviceDocument2: Device = { id: 'd-id-2', name: 'device-2', site: 'LOC-B' };
      const insertedDeviceDocument3: Device = { id: 'd-id-3', name: 'device-3', site: 'LOC-B' };

      deviceCollection.insertMany([insertedDeviceDocument1, insertedDeviceDocument2, insertedDeviceDocument3]);

      const deviceNames: string[] = await deviceDao.getDistinct('name', {
        limit: 10,
        filter: JSON.stringify(<Filter[]>[{ field: 'site', operator: '=', value: 'LOC-B' }]),
      });

      expect(deviceNames).toEqual(['device-2', 'device-3']);
    });
  });
});
