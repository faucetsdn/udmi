import { Collection, Db, MongoClient, FindCursor } from 'mongodb';
import { MongoDeviceDAO } from '../../../../device/dao/mongodb/MongoDeviceDAO';
import { Device, SearchOptions } from '../../../../device/model';
import { createDevices } from '../../data';

let connection: MongoClient;
let db: Db;
let deviceCollection: Collection;
let mongoDeviceDAO: MongoDeviceDAO;
let mockDevices: Device[];
let findSpy;
let limitSpy;
let skipSpy;

beforeEach(async () => {
  // in memory mongo
  connection = await MongoClient.connect(process.env.MONGO_URL);
  db = await connection.db('tmp');

  // object under test
  mongoDeviceDAO = new MongoDeviceDAO(db);

  // data prep
  mockDevices = createDevices(100);
  deviceCollection = db.collection('device');
  deviceCollection.insertMany(mockDevices);

  // spies
  findSpy = jest.spyOn(Collection.prototype, 'find');
  limitSpy = jest.spyOn(FindCursor.prototype, 'limit');
  skipSpy = jest.spyOn(FindCursor.prototype, 'skip');
});

afterEach(() => {
  db.dropCollection('device');
  jest.resetAllMocks();
  jest.restoreAllMocks();
});

describe('MongoDeviceDAO.getDevices', () => {
  test('all devices are returned if searchOption does not specify a filter', async () => {
    const searchOptions: SearchOptions = { batchSize: 100, offset: 0 };

    const retrievedDevices: Device[] = await mongoDeviceDAO.getDevices(searchOptions);

    expect(retrievedDevices).toEqual(mockDevices);
    expect(findSpy).toHaveBeenCalledWith({});
  });

  test('filtering returns devices with AHU in their name if searchOption specifies a filter of name contains AHU', async () => {
    const value = 'AHU';
    const searchOptions: SearchOptions = {
      batchSize: 100,
      offset: 0,
      filter: `[{"field":"name","operator":"~","value":"${value}"}]`,
    };

    const retrievedDevices: Device[] = await mongoDeviceDAO.getDevices(searchOptions);

    retrievedDevices.forEach((device) => {
      expect(device.name.includes(value)).toBe(true);
    });
    expect(findSpy).toHaveBeenCalledWith({ name: { $regex: value, $options: 'i' } });
  });

  test('limit returns a number of devices <= to the limit provided', async () => {
    const searchOptions: SearchOptions = { batchSize: 10, offset: 0 };

    const retrievedDevices: Device[] = await mongoDeviceDAO.getDevices(searchOptions);

    expect(retrievedDevices.length <= 10).toBeTruthy();
    expect(limitSpy).toHaveBeenCalledWith(10);
  });

  test.each([
    [999, 999],
    [1000, 1000],
    [1001, 1000],
  ])('limit is reduced to 1000 if a value greater than 1000', async (value, expected) => {
    const searchOptions: SearchOptions = { batchSize: value, offset: 0 };

    await mongoDeviceDAO.getDevices(searchOptions);

    expect(limitSpy).toHaveBeenCalledWith(expected);
  });

  test('skip is called with 0 if an offset was not provided', async () => {
    const searchOptions: SearchOptions = { batchSize: 10 };

    await mongoDeviceDAO.getDevices(searchOptions);

    expect(skipSpy).toHaveBeenCalledWith(0);
  });

  test('if offset is provided, skip is called with the offset', async () => {
    const searchOptions: SearchOptions = { batchSize: 10, offset: 20 };

    const retrievedDevices: Device[] = await mongoDeviceDAO.getDevices(searchOptions);

    let n = 21;
    while (n < retrievedDevices.length) {
      const device = retrievedDevices[n];
      expect(device.id).toBe(n);
      n++;
    }
    expect(skipSpy).toHaveBeenCalledWith(20);
  });

  test('no devices are returned if the offset is greater than the device count', async () => {
    const searchOptions: SearchOptions = { batchSize: 10, offset: 101 };

    const retrievedDevices: Device[] = await mongoDeviceDAO.getDevices(searchOptions);

    expect(retrievedDevices.length).toBe(0);
  });
});

describe('MongoDeviceDAO.getDeviceCount', () => {
  test('returns the device count in the mongo db', async () => {
    const count: number = await mongoDeviceDAO.getDeviceCount();

    expect(count).toBe(mockDevices.length);
  });
});
