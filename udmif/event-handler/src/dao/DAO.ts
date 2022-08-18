import { Collection, Filter } from 'mongodb';
import { Site } from '../site/model/Site';
import { Device } from '../device/model/Device';
import { getMongoCollection } from './MongoCollectionProvider';

export async function getDeviceDAO(): Promise<DefaultDAO<Device>> {
  return new DefaultDAO<Device>(await getMongoCollection<Device>('device'));
}

export async function getSiteDAO(): Promise<DefaultDAO<Site>> {
  return new DefaultDAO<Site>(await getMongoCollection<Site>('site'));
}

export interface DAO<Type> {
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
}
