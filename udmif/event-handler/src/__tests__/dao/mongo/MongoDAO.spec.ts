import { DAO } from '../../../dao/DAO';
import {
  MongoDAO,
  getDeviceDAO,
  getSiteDAO,
  getSiteValidationDAO,
  getDeviceValidationDAO,
} from '../../../dao/mongo/MongoDAO';
import { Collection, MongoClient, Db } from 'mongodb';
import { Device, DeviceKey } from '../../../device/model/Device';
import { Site, SiteValidation } from '../../../site/model/Site';

const mockClient = jest.fn().mockImplementation(() => {
  return {
    db: () => {
      return { collection: jest.fn(), collections: jest.fn().mockReturnValue([]), createCollection: jest.fn() };
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

  test('returns a SiteDAO object', () => {
    expect(getSiteDAO()).toBeTruthy();
  });

  test('returns a SiteValidationDAO object', () => {
    expect(getSiteValidationDAO()).toBeTruthy();
  });

  test('returns a DeviceValidationDAO object', () => {
    expect(getDeviceValidationDAO()).toBeTruthy();
  });
});

describe('DAO<Device>', () => {
  let deviceDao: DAO<Device>;
  let deviceCollection: Collection<Device>;

  const siteName: string = 'site-2';
  const id: string = 'num-1';
  const name: string = 'name';
  const deviceKey: DeviceKey = { name, site: siteName };

  beforeEach(async () => {
    deviceCollection = db.collection('device');
    await deviceCollection.deleteMany({});
    deviceDao = new MongoDAO<Device>(deviceCollection);
  });

  test('upsert calls the updateOne method on the provided collection', () => {
    // arrange
    const device: Device = { name, id, site: siteName };
    const updateOneSpy = jest.spyOn(deviceCollection, 'updateOne').mockImplementation(jest.fn());

    // act
    deviceDao.upsert(deviceKey, device);

    // assert
    expect(updateOneSpy).toHaveBeenCalledWith(deviceKey, { $set: device }, { upsert: true });
  });

  test('get method is called and returns the matching document', async () => {
    // arrange
    const findOneSpy = jest.spyOn(deviceCollection, 'findOne');
    const insertedDeviceDocument: Device = { name, site: siteName, id, points: [], serialNumber: 'randomSerialId' };
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
    await siteCollection.deleteMany({});
    siteDao = new MongoDAO<Site>(siteCollection);
  });

  test('upsert calls the updateOne method on the provided collection', () => {
    // arrange
    const site: Site = { name: 'site', validation: { name: '' } };
    const updateOneSpy = jest.spyOn(siteCollection, 'updateOne').mockImplementation(jest.fn());

    // act
    siteDao.upsert(siteKey, site);

    // assert
    expect(updateOneSpy).toHaveBeenCalledWith(siteKey, { $set: site }, { upsert: true });
  });

  test('get method is called and returns the matching document', async () => {
    // arrange
    const findOneSpy = jest.spyOn(siteCollection, 'findOne');
    const insertedDocument: Site = { name: siteName, validation: { name: '' } };
    siteCollection.insertOne(insertedDocument);

    // act
    const retrievedDocument: Site = await siteDao.get(siteKey);

    // assert
    expect(retrievedDocument).toEqual(insertedDocument);
    expect(findOneSpy).toHaveBeenCalledWith(siteKey);
  });
});

describe('DAO<SiteValidation>', () => {
  let siteValidationDao: DAO<SiteValidation>;
  let siteValidationCollection: Collection<SiteValidation>;

  beforeEach(async () => {
    siteValidationCollection = db.collection('site_validation');
    await siteValidationCollection.deleteMany({});
    siteValidationDao = new MongoDAO<SiteValidation>(siteValidationCollection);
  });

  test('insert calls the insertOne method on the provided collection', () => {
    // arrange
    const siteValidation: SiteValidation = { timestamp: new Date(), siteName: 'siteValidation', data: {} };
    const insertOneSpy = jest.spyOn(siteValidationCollection, 'insertOne').mockImplementation(jest.fn());

    // act
    siteValidationDao.insert(siteValidation);

    // assert
    expect(insertOneSpy).toHaveBeenCalledWith(siteValidation);
  });
});
