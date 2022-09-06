import { getCollection, getTimeSeriesCollection, getUri } from '../../../dao/mongo/MongoCollectionProvider';
import { Collection, MongoClient, TimeSeriesCollectionOptions } from 'mongodb';
import { Site, SiteValidation } from '../../../site/model/Site';

const mockCollection = jest.fn();
const mockCollections = jest.fn();

const mockClient = jest.fn().mockImplementation(() => {
  return {
    db: () => {
      return { collection: mockCollection, collections: mockCollections, createCollection: jest.fn() };
    },
  };
});

beforeEach(() => {
  //mock the static MongoClient.connect here
  MongoClient.connect = mockClient;
});

describe('MongoCollectionProvider', () => {
  describe('getUri()', () => {
    // take a backup of the environment prior to running the tests
    const ENV_BACKUP = { ...process.env };

    afterEach(() => {
      process.env = { ...ENV_BACKUP }; // Restore old environment
    });

    test('returns a uri with a host', () => {
      process.env.MONGO_HOST = 'host:8001';
      expect(getUri()).toEqual('undefined://host:8001');
    });
    test('returns a uri with a protocol', () => {
      process.env.MONGO_PROTOCOL = 'mongodb';
      expect(getUri()).toEqual('mongodb://undefined');
    });
    test('returns a uri with a user and password', () => {
      process.env.MONGO_PROTOCOL = 'mongodb';
      process.env.MONGO_HOST = 'host:8001';
      process.env.MONGO_USER = 'user';
      process.env.MONGO_PWD = 'pwd';
      expect(getUri()).toEqual('mongodb://user:pwd@host:8001');
    });
  });

  describe('getMongoCollection', () => {
    test('returns a collection object', async () => {
      // arrange
      mockCollection.mockReturnValue({});
      // act
      const collection: Collection<Site> = await getCollection<Site>('site');
      // assert
      expect(collection).toBeTruthy();
    });
  });

  describe('getMongoTimeSeriesCollection', () => {
    const timeSeriesOptions: TimeSeriesCollectionOptions = { timeField: 'someField' };

    test('returns a collection object', async () => {
      // arrange
      const collectionName: string = 'site_validation';
      const collection = { collectionName: collectionName };
      mockCollection.mockReturnValue(collection);
      mockCollections.mockReturnValue([collection]);
      // act
      const siteValcollection: Collection<SiteValidation> = await getTimeSeriesCollection<SiteValidation>(
        collectionName,
        timeSeriesOptions
      );
      // assert
      expect(siteValcollection).toBeTruthy();
    });

    test('logs getting the collection if it exists', async () => {
      // arrange
      jest.spyOn(global.console, 'log');
      const collectionName: string = 'site_validation';
      const collection = { collectionName: collectionName };
      mockCollection.mockReturnValue(collection);
      mockCollections.mockReturnValue([collection]);
      // act
      await getTimeSeriesCollection<SiteValidation>(collectionName, timeSeriesOptions);
      // assert
      expect(console.log).toHaveBeenCalledWith('Getting the Mongo Collection collection: site_validation');
    });

    test('logs creating the collection if it does not exist', async () => {
      // arrange
      jest.spyOn(global.console, 'log');
      const collectionName: string = 'tmp_coll';
      const collection = { collectionName: collectionName };
      mockCollection.mockReturnValue(collection);
      mockCollections.mockReturnValue([collection]);
      // act
      await getTimeSeriesCollection<SiteValidation>('site_validation', timeSeriesOptions);
      // assert
      expect(console.log).toHaveBeenCalledWith('Creating the site_validation collection.');
    });
  });
});
