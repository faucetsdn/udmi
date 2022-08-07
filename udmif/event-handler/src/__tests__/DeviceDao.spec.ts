import { DefaultDeviceDao, DeviceDao } from '../DeviceDao';
import { Collection, MongoClient, Db } from 'mongodb';
import { DeviceKey } from '../DeviceKey';
import { DeviceDocument } from '../DeviceDocument';

// mongo collection
let deviceCollection: Collection<DeviceDocument>;
let connection: MongoClient;
let db: Db;

beforeAll(async () => {
  // in memory mongo
  connection = await MongoClient.connect(process.env.MONGO_URL);
  db = connection.db('tmp');
  deviceCollection = db.collection('device');
});

afterAll(async () => {
  // make sure we close it before the test completely finish
  await connection.close();
});

beforeEach(async () => {
  // clean the collection before each test
  await deviceCollection.deleteMany({});
})

describe('DeviceDao.upsert', () => {
  test('upsert calls the updateOne method on the provided collection', () => {
    // arrange
    const name: string = 'name';
    const id: string = 'id';
    const deviceKey: DeviceKey = { name, id };
    const deviceDocument: DeviceDocument = { name, id };
    const deviceDao: DeviceDao = new DefaultDeviceDao(deviceCollection);
    const updateOneSpy = jest.spyOn(deviceCollection, 'updateOne').mockImplementation(jest.fn());

    // act
    deviceDao.upsert(deviceKey, deviceDocument);

    // assert
    expect(updateOneSpy).toHaveBeenCalledWith(deviceKey, { $set: deviceDocument }, { upsert: true });
  });
});

describe('DeviceDao.get', () => {

  test('get method is called and returns the matching document', async () => {
    // arrange
    const findOneSpy = jest.spyOn(deviceCollection, 'findOne');

    const name: string = 'name';
    const id: string = 'id';
    const deviceKey: DeviceKey = { name, id };
    const deviceDao: DeviceDao = new DefaultDeviceDao(deviceCollection);

    const insertedDeviceDocument: DeviceDocument = { name, id, points: [], serialNumber: 'randomSerialId' };
    deviceCollection.insertOne(insertedDeviceDocument);

    // act
    const retrievedDeviceDocument: DeviceDocument = await deviceDao.get(deviceKey);

    // assert
    expect(retrievedDeviceDocument).toEqual(insertedDeviceDocument);
    expect(findOneSpy).toHaveBeenCalledWith(deviceKey);
  });
});