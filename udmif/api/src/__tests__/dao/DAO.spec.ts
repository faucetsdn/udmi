import { Collection, MongoClient, Db } from 'mongodb';
import { DAO } from '../../dao/DAO';
import { DefaultDAO } from '../../dao/DAO';
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

  test('getAll', async () => {
    const insertedDeviceDocument: Device = { id: 'd-id-1', name: 'device-1', site: 'LOC' };

    deviceCollection.insertOne(insertedDeviceDocument);

    const devides: Device[] = await deviceDao.getAll({ batchSize: 10, offset: 0 });

    expect(devides).toEqual([insertedDeviceDocument]);
  });

  test('getOne', async () => {
    const insertedDeviceDocument: Device = { id: 'd-id-1', name: 'device-1', site: 'LOC' };

    deviceCollection.insertOne(insertedDeviceDocument);

    const device: Device = await deviceDao.getOne({ id: 'd-id-1' });

    expect(device).toEqual(insertedDeviceDocument);
  });

  test('getFilteredCount', async () => {
    const insertedDeviceDocument1: Device = { id: 'd-id-1', name: 'device-1', site: 'LOC' };
    const insertedDeviceDocument2: Device = { id: 'd-id-2', name: 'device-2', site: 'LOC' };

    deviceCollection.insertMany([insertedDeviceDocument1, insertedDeviceDocument2]);

    const filteredCount: number = await deviceDao.getFilteredCount({
      batchSize: 10,
      offset: 0,
      filter: '[{"field":"name","operator":"~","value":"2"}]',
    });

    expect(filteredCount).toEqual(1);
  });

  test('getCount', async () => {
    const insertedDeviceDocument1: Device = { id: 'd-id-1', name: 'device-1', site: 'LOC' };
    const insertedDeviceDocument2: Device = { id: 'd-id-2', name: 'device-2', site: 'LOC' };

    deviceCollection.insertMany([insertedDeviceDocument1, insertedDeviceDocument2]);

    const count: number = await deviceDao.getCount();

    expect(count).toEqual(2);
  });

  test('getDistinct', async () => {
    const insertedDeviceDocument1: Device = { id: 'd-id-1', name: 'device-1', site: 'LOC' };
    const insertedDeviceDocument2: Device = { id: 'd-id-2', name: 'device-2', site: 'LOC' };

    deviceCollection.insertMany([insertedDeviceDocument1, insertedDeviceDocument2]);

    const deviceNames: string[] = await deviceDao.getDistinct('name', { limit: 10 });

    expect(deviceNames).toEqual(['device-1', 'device-2']);
  });
});
