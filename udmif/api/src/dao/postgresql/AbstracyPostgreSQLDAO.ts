import { Knex } from 'knex';
import { ValidatedSearchOptions, ValidatedDistinctSearchOptions, SortOptions } from '../../common/model';
import { DAO } from '../DAO';
import { Order, getOrderByOptions } from './OrderBy';
import { getWhereOptions } from './Where';

export abstract class AbstractPostgreSQLDAO<Type> implements DAO<Type> {
  defaultOrder: Order;

  constructor(private db: Knex, private tableName: string) {}

  getAll(searchOptions: ValidatedSearchOptions): Promise<Type[] | null> {
    return this.getTable()
      .select()
      .orderBy(this.getOrderBy(searchOptions.sortOptions))
      .where((builder) => this.getWhere(searchOptions.filter, builder))
      .limit(searchOptions.batchSize)
      .offset(searchOptions.offset);
  }

  getOne(filterQuery: any): Promise<Type | null> {
    throw new Error('Method not implemented.');
  }

  getFilteredCount(searchOptions: ValidatedSearchOptions): Promise<number> {
    throw new Error('Method not implemented.');
  }

  getCount(): Promise<number> {
    throw new Error('Method not implemented.');
  }

  getDistinct(field: string, searchOptions: ValidatedDistinctSearchOptions): Promise<string[] | null> {
    throw new Error('Method not implemented.');
  }

  private getTable(): Knex.QueryBuilder<any, any[]> {
    return this.db(this.tableName);
  }

  private getWhere(filter: string, builder: Knex.QueryBuilder<any, any[]>) {
    getWhereOptions(filter).forEach((filter) => builder.where(filter.field, filter.operator, filter.values));
  }

  private getOrderBy(searchOptions: SortOptions): Order[] {
    let orderOptions: Order[] = getOrderByOptions(searchOptions);

    // add a default sort to the sort options
    if (this.defaultOrder) orderOptions.push(this.defaultOrder);

    return orderOptions;
  }
}
