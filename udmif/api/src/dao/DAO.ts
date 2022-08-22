import { Collection, Filter } from 'mongodb';
import { Configuration } from '../server/config';
import { Device } from '../device/model';
import { Site } from '../site/model';
import { getMongoCollection } from '../mongo/MongoCollectionProvider';
import { getAggregate } from '../mongo/MongoAggregateBuilder';
import { getFilter } from '../mongo/MongoFilterBuilder';
import { getSort } from '../mongo/MongoSortBuilder';
import { fromString } from '../common/FilterParser';
import { SearchOptions, ValidatedCommonSearchOptions } from '../common/model';

export async function getDeviceDAO(systemConfiguration: Configuration): Promise<DefaultDAO<Device>> {
  return new DefaultDAO<Device>(await getMongoCollection<Device>('device', systemConfiguration));
}

export async function getSiteDAO(systemConfiguration: Configuration): Promise<DefaultDAO<Site>> {
  return new DefaultDAO<Site>(await getMongoCollection<Site>('site', systemConfiguration));
}

export interface DAO<Type> {
  getAll(searchOptions: SearchOptions): Promise<Type[]>;
  getOne(filterQuery: any): Promise<Type>;
  getFilteredCount(searchOptions: SearchOptions): Promise<number>;
  getCount(): Promise<number>;
  getDistinct(field: string, searchOptions: ValidatedCommonSearchOptions): Promise<string[]>;
}

/**
 * This will create a Data Access Object for the type you specify.
 */
export class DefaultDAO<Type> implements DAO<Type> {
  constructor(private collection: Collection<Type>) {}

  /**
   * Returns all the entries for the type of collection specified
   * based on any search options provided.
   */
  async getAll(searchOptions: SearchOptions): Promise<Type[]> {
    return this.collection
      .find<Type>(this.getFilter(searchOptions))
      .sort(this.getSort(searchOptions))
      .skip(searchOptions.offset)
      .limit(searchOptions.batchSize)
      .toArray();
  }

  /**
   * Finds and returns a single entry from the collection based on
   * a mongo filter.
   */
  async getOne(filterQuery: Filter<Type>): Promise<Type> {
    return this.collection.findOne<Type>(filterQuery);
  }

  /**
   * Returns the number of entries in the collection
   * based on any search options provided.
   */
  async getFilteredCount(searchOptions: SearchOptions): Promise<number> {
    return this.collection.countDocuments(this.getFilter(searchOptions));
  }

  /**
   * Returns the number of entries in the collection.
   */
  async getCount(): Promise<number> {
    return this.collection.countDocuments();
  }

  /**
   * Returns an array of unique results off a field based on any search
   * options provided.
   */
  async getDistinct(field: string, searchOptions: ValidatedCommonSearchOptions): Promise<string[]> {
    return this.collection
      .aggregate(getAggregate(field, searchOptions.limit, searchOptions.search))
      .map((entity: Type) => entity[field])
      .toArray();
  }

  private getFilter(searchOptions: SearchOptions): Filter<Type> {
    return searchOptions.filter ? getFilter(fromString(searchOptions.filter)) : {};
  }

  private getSort(searchOptions: SearchOptions): any {
    return searchOptions.sortOptions ? getSort(searchOptions.sortOptions) : {};
  }
}
