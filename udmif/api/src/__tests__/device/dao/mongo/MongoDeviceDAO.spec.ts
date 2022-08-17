import { Collection, Db, MongoClient, FindCursor } from 'mongodb';
import { MongoDeviceDAO } from '../../../../device/dao/mongodb/MongoDeviceDAO';
import { Device, Point, SearchOptions, SORT_DIRECTION } from '../../../../device/model';
import { createDevices } from '../../data';

const mockDevices: Device[] = createDevices(100);

// mongo objects
let connection: MongoClient;
let db: Db;
let deviceCollection: Collection;

// object under test
let mongoDeviceDAO: MongoDeviceDAO;

// spies
let findSpy;
let sortSpy;
let limitSpy;
let skipSpy;

beforeAll(async () => {
  // in memory mongo
  connection = await MongoClient.connect(process.env.MONGO_URL);
  db = await connection.db('tmp');
});

afterAll(async () => {
  await connection.close();
});

beforeEach(async () => {
  mongoDeviceDAO = new MongoDeviceDAO(db);

  // data prep
  deviceCollection = db.collection('device');
  await deviceCollection.insertMany(mockDevices);

  findSpy = jest.spyOn(Collection.prototype, 'find');
  sortSpy = jest.spyOn(FindCursor.prototype, 'sort');
  limitSpy = jest.spyOn(FindCursor.prototype, 'limit');
  skipSpy = jest.spyOn(FindCursor.prototype, 'skip');
});

afterEach(async () => {
  await db.collection('device').deleteMany({});

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

  test('not sorting is done if the searchOption does not specify sortOptions', async () => {
    const searchOptions: SearchOptions = { batchSize: 100, offset: 0 };

    await mongoDeviceDAO.getDevices(searchOptions);

    expect(sortSpy).toHaveBeenCalledWith({});
  });

  test('sorting can be done in ascending order', async () => {
    const searchOptions: SearchOptions = {
      batchSize: 100,
      offset: 0,
      sortOptions: { field: 'name', direction: SORT_DIRECTION.ASC },
    };

    await mongoDeviceDAO.getDevices(searchOptions);

    expect(sortSpy).toHaveBeenCalledWith({ name: 1 });
  });

  test('sorting can be done in descending order', async () => {
    const searchOptions: SearchOptions = {
      batchSize: 100,
      offset: 0,
      sortOptions: { field: 'name', direction: SORT_DIRECTION.DESC },
    };

    await mongoDeviceDAO.getDevices(searchOptions);

    expect(sortSpy).toHaveBeenCalledWith({ name: -1 });
  });

  test('that the filter is applied and devices returned are limited to those matching the filter', async () => {
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
  });

  test('limit returns a number of devices <= to the limit provided', async () => {
    const searchOptions: SearchOptions = { batchSize: 10, offset: 0 };

    const retrievedDevices: Device[] = await mongoDeviceDAO.getDevices(searchOptions);

    expect(retrievedDevices.length <= 10).toBeTruthy();
    expect(limitSpy).toHaveBeenCalledWith(10);
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

describe('MongoDeviceDAO.getFilteredDeviceCount', () => {
  test('returns the filtered device count in the mongo db', async () => {
    const searchOptions: SearchOptions = { batchSize: 10 };

    const filterdDeviceCount: number = await mongoDeviceDAO.getFilteredDeviceCount(searchOptions);

    expect(filterdDeviceCount).toBe(mockDevices.length);
  });
});

describe('MongoDeviceDAO.getDevice', () => {
  test('returns null if the device with the same id is not in the mongo db', async () => {
    const device: Device = await mongoDeviceDAO.getDevice('some-id');
    expect(device).toBe(null);
  });

  test('returns the device with the same id that is in the mongodb', async () => {
    const device = await db.collection<Device>('device').findOne({ make: new RegExp('make', 'i') });
    const retrievedDevice: Device = await mongoDeviceDAO.getDevice(device.id);
    expect(retrievedDevice.id).toBe(device.id);
  });

  test('returns a device with some Points', async () => {
    const device = await db.collection<Device>('device').findOne({ make: new RegExp('make', 'i') });
    const retrievedDevice: Device = await mongoDeviceDAO.getDevice(device.id);
    expect(retrievedDevice.points).not.toEqual([]);
  });
});

describe('MongoDeviceDAO.getPoints', () => {
  test('returns empty array if the device with the same id is not in the mongo db', async () => {
    const points: Point[] = await mongoDeviceDAO.getPoints('some-id');
    expect(points).toEqual([]);
  });

  test('returns the points for the device id that is in the mongodb', async () => {
    const device = await db.collection<Device>('device').findOne({ make: new RegExp('make', 'i') });
    const points: Point[] = await mongoDeviceDAO.getPoints(device.id);
    expect(points).toEqual(device.points);
  });
});

describe('MongoDeviceDAO.getDeviceNames', () => {
  test('unique device names are returned', async () => {
    const retrievedDeviceNames: string[] = await mongoDeviceDAO.getDeviceNames({ limit: 10 });

    expect(retrievedDeviceNames).toBeDistinct();
    expect(retrievedDeviceNames.length).toBeWithinRange(0, 10);
  });
});

describe('MongoDeviceDAO.getDeviceMakes', () => {
  test('unique device makes are returned', async () => {
    const retrievedDeviceMakes: string[] = await mongoDeviceDAO.getDeviceMakes({ limit: 10 });

    expect(retrievedDeviceMakes).toBeDistinct();
    expect(retrievedDeviceMakes.length).toBeWithinRange(0, 10);
  });
});

describe('MongoDeviceDAO.getDeviceModels', () => {
  test('unique device models are returned', async () => {
    const retrievedDeviceModels: string[] = await mongoDeviceDAO.getDeviceModels({ limit: 10 });

    expect(retrievedDeviceModels).toBeDistinct();
    expect(retrievedDeviceModels.length).toBeWithinRange(0, 10);
  });
});

describe('MongoDeviceDAO.getSiteNames', () => {
  test('unique sites are returned', async () => {
    const retrievedSites: string[] = await mongoDeviceDAO.getSiteNames({ limit: 10 });

    expect(retrievedSites).toBeDistinct();
    expect(retrievedSites.length).toBeWithinRange(0, 10);
  });
});

describe('MongoDeviceDAO.getSections', () => {
  test('unique sections are returned', async () => {
    const retrievedSections: string[] = await mongoDeviceDAO.getSections({ limit: 10 });

    expect(retrievedSections).toBeDistinct();
    expect(retrievedSections.length).toBeWithinRange(0, 10);
  });
});
