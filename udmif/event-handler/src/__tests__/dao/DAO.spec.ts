import { DAO, DefaultDAO, getDeviceDAO, getSiteDAO } from '../../dao/DAO';
import { Collection, MongoClient, Db } from 'mongodb';
import { Device, DeviceKey } from '../../device/model/Device';
import { Site } from '../../site/model/Site';

const mockClient = jest.fn().mockImplementation(() => {
  return {
    db: () => {
      return { collection: jest.fn() };
    },
  };
});

// mongo collection
let connection: MongoClient;
let db: Db;

beforeAll(async () => {
  // in memory mongo
  connection = await MongoClient.connect(process.env.MONGO_URL);
  db = connection.db('tmp');
});

afterAll(async () => {
  // make sure we close it before the test completely finish
  await connection.close();
});

beforeEach(async () => {
  jest.resetModules();
  jest.clearAllMocks();

  //mock the static MongoClient.connect here
  MongoClient.connect = mockClient;
});

describe('DAO.getxxxDAO()', () => {
  test('returns a DeviceDao object', () => {
    expect(getDeviceDAO()).toBeTruthy();
  });

  test('returns a SystemDAO object', () => {
    expect(getSiteDAO()).toBeTruthy();
  });
});

describe('DAO<Device>', () => {
  let deviceDao: DAO<Device>;
  let deviceCollection: Collection<Device>;

  const siteName: string = 'site-2';
  const name: string = 'name';
  const deviceKey: DeviceKey = { name, site: siteName };

  beforeEach(async () => {
    deviceCollection = db.collection('device');
    deviceDao = new DefaultDAO<Device>(deviceCollection);
    await deviceCollection.deleteMany({});
  });

  test('upsert calls the updateOne method on the provided collection', () => {
    // arrange
    const device: Device = { name, site: siteName };
    const updateOneSpy = jest.spyOn(deviceCollection, 'updateOne').mockImplementation(jest.fn());

    // act
    deviceDao.upsert(deviceKey, device);

    // assert
    expect(updateOneSpy).toHaveBeenCalledWith(deviceKey, { $set: device }, { upsert: true });
  });

  test('get method is called and returns the matching document', async () => {
    // arrange
    const findOneSpy = jest.spyOn(deviceCollection, 'findOne');
    const insertedDeviceDocument: Device = { name, site: siteName, points: [], serialNumber: 'randomSerialId' };
    deviceCollection.insertOne(insertedDeviceDocument);

    // act
    const retrievedDocument: Device = await deviceDao.get(deviceKey);

    // assert
    expect(retrievedDocument).toEqual(insertedDeviceDocument);
    expect(findOneSpy).toHaveBeenCalledWith(deviceKey);
  });
});

describe('DAO<Site>', () => {
  let siteDao: DAO<Site>;
  let siteCollection: Collection<Site>;

  const siteName: string = 'site-1';
  const siteKey = { name: siteName };

  beforeEach(async () => {
    siteCollection = db.collection('site');
    siteDao = new DefaultDAO<Site>(siteCollection);
    await siteCollection.deleteMany({});
  });

  test('upsert calls the updateOne method on the provided collection', () => {
    // arrange
    const site: Site = { name: 'site' };
    const updateOneSpy = jest.spyOn(siteCollection, 'updateOne').mockImplementation(jest.fn());

    // act
    siteDao.upsert(siteKey, site);

    // assert
    expect(updateOneSpy).toHaveBeenCalledWith(siteKey, { $set: site }, { upsert: true });
  });

  test('get method is called and returns the matching document', async () => {
    // arrange
    const findOneSpy = jest.spyOn(siteCollection, 'findOne');
    const insertedDocument: Site = { name: siteName };
    siteCollection.insertOne(insertedDocument);

    // act
    const retrievedDocument: Site = await siteDao.get(siteKey);

    // assert
    expect(retrievedDocument).toEqual(insertedDocument);
    expect(findOneSpy).toHaveBeenCalledWith(siteKey);
  });
});
