import { Collection, Filter, TimeSeriesCollectionOptions } from 'mongodb';
import { Site, SiteValidation } from '../site/model/Site';
import { Device } from '../device/model/Device';
import { getCollection, getTimeSeriesCollection } from './MongoCollectionProvider';

export async function getDeviceDAO(): Promise<DefaultDAO<Device>> {
  return new DefaultDAO<Device>(await getCollection<Device>('device'));
}

export async function getSiteDAO(): Promise<DefaultDAO<Site>> {
  return new DefaultDAO<Site>(await getCollection<Site>('site'));
}

export async function getSiteValidationDAO(): Promise<DefaultDAO<SiteValidation>> {
  const collectionOptions: TimeSeriesCollectionOptions = {
    timeField: 'timestamp',
    metaField: 'siteName',
    granularity: 'minutes',
  };
  const collection: Collection<SiteValidation> = await getTimeSeriesCollection<SiteValidation>(
    'site_validation',
    collectionOptions
  );
  return new DefaultDAO<SiteValidation>(collection);
}

export interface DAO<Type> {
  insert(document: Type): Promise<void>;
  upsert(filterQuery: any, updateQuery: any): Promise<void>;
  get(filterQuery: any): Promise<Type>;
}

/**
 * This will create a Data Access Object for the type you specify.
 */
export class DefaultDAO<Type> implements DAO<Type> {
  constructor(private collection: Collection<Type>) {}

  /**
   * Updates a device document if it is found using the device key, else it will insert a new device document
   * @param {Type} filter, a filter for type {Type}
   * @param {Type} document, a document of type {Type}
   */
  async upsert(filter: Filter<Type>, document: Type): Promise<void> {
    // we're using upsert which will allow document updates if it already exists and a document cretion if it does not
    console.log('Attempting to Upsert the following document: ' + JSON.stringify(document));

    const result = await this.collection.updateOne(filter, { $set: document }, { upsert: true });

    // let's log the result to give us some feedback on what occurred during the upsert
    console.log('Upsert result: ' + JSON.stringify(result));
  }

  /**
   * Gets a single document using the provided filter
   * @param {Type} filter, the query for the get operation
   * @returns {Type} a document if it was found
   */
  async get(filter: Filter<Type>): Promise<Type> {
    return this.collection.findOne<Type>(filter);
  }

  async insert(document: Type): Promise<void> {
    const result = await this.collection.insertOne(document as any);
    // let's log the result to give us some feedback on what occurred during the insert
    console.log('insert result: ' + JSON.stringify(result));
  }
}
