import { Knex } from 'knex';
import { ValidatedSearchOptions, ValidatedDistinctSearchOptions, SortOptions } from '../../common/model';
import { DAO } from '../DAO';
import { Order, getOrderByOptions } from './OrderBy';
import { getWhereOptions } from './Where';

export abstract class AbstractPostgreSQLDAO<TYPE> implements DAO<TYPE> {
  defaultOrder: Order;

  constructor(private db: Knex, private tableName: string) {}

  getAll(searchOptions: ValidatedSearchOptions): Promise<TYPE[]> {
    return this.getTable()
      .select()
      .orderBy(this.getOrderBy(searchOptions.sortOptions))
      .where((builder) => {
        getWhereOptions(searchOptions.filter).forEach((filter) =>
          builder.where(filter.field, filter.operator, filter.values)
        );
      })
      .limit(searchOptions.batchSize);
  }

  getOne(filterQuery: any): Promise<TYPE> {
    throw new Error('Method not implemented.');
  }

  getFilteredCount(searchOptions: ValidatedSearchOptions): Promise<number> {
    throw new Error('Method not implemented.');
  }

  getCount(): Promise<number> {
    throw new Error('Method not implemented.');
  }

  getDistinct(field: string, searchOptions: ValidatedDistinctSearchOptions): Promise<string[]> {
    throw new Error('Method not implemented.');
  }

  private getTable() {
    return this.db(this.tableName);
  }

  private getOrderBy(searchOptions: SortOptions): Order[] {
    let orderOptions: Order[] = getOrderByOptions(searchOptions);

    // add a default sort to the sort options
    if (this.defaultOrder) orderOptions.push(this.defaultOrder);

    return orderOptions;
  }
}
