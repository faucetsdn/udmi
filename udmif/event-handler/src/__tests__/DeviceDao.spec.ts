import { DefaultDeviceDao, DeviceDao } from '../DeviceDao';
import { Collection, MongoClient, Db } from 'mongodb';
import { DeviceDocument, DeviceKey } from '../model';

// mongo collection
let deviceCollection: Collection;

beforeAll(async () => {
  // in memory mongo
  const connection: MongoClient = await MongoClient.connect(process.env.MONGO_URL);
  const db: Db = await connection.db('tmp');
  deviceCollection = db.collection('device');
});

describe('DeviceDao.upsert', () => {
  test('upsert calls the updateOne method on the provided collection', () => {
    // arrange
    const name: string = 'name';
    const id: string = 'id';
    const key: DeviceKey = { name, id };
    const deviceDocument: DeviceDocument = { name, id };
    const deviceDao: DeviceDao = new DefaultDeviceDao(deviceCollection);
    const updateOneSpy = jest.spyOn(deviceCollection, 'updateOne').mockImplementation(jest.fn());

    // act
    deviceDao.upsert(key, deviceDocument);

    // assert
    expect(updateOneSpy).toHaveBeenCalledWith(key, { $set: deviceDocument }, { upsert: true });
  });
});
