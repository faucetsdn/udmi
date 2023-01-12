import { Knex } from 'knex';
import { ValidatedSearchOptions, ValidatedDistinctSearchOptions, SortOptions } from '../../common/model';
import { DAO } from '../DAO';
import { Order, getOrderByOptions } from './OrderBy';
import { getWhereOptions } from './Where';

export abstract class AbstractPostgreSQLDAO<Type> implements DAO<Type> {
  defaultOrder: Order;
  ID_AS_COUNT = 'uuid as count';

  constructor(private db: Knex, private tableName: string) {}

  async getAll(searchOptions: ValidatedSearchOptions): Promise<Type[]> {
    return this.getTable()
      .select()
      .orderBy(this.getOrderBy(searchOptions.sortOptions))
      .where((builder) => this.addWhereClause(searchOptions.filter, builder))
      .limit(searchOptions.batchSize)
      .offset(searchOptions.offset);
  }

  async getOne(filterQuery: any): Promise<Type> {
    return this.getTable()
      .where(filterQuery)
      .first()
      .then((row) => row);
  }

  async getFilteredCount(searchOptions: ValidatedSearchOptions): Promise<number> {
    return this.getTable()
      .select()
      .where((builder) => this.addWhereClause(searchOptions.filter, builder))
      .count(this.ID_AS_COUNT)
      .first()
      .then((row) => row.count);
  }

  async getCount(): Promise<number> {
    return this.getTable()
      .count(this.ID_AS_COUNT)
      .first()
      .then((row) => row.count);
  }

  async getDistinct(field: string, searchOptions: ValidatedDistinctSearchOptions): Promise<string[]> {
    return this.getTable()
      .select(field)
      .where((builder) => this.addWhereClause(searchOptions.filter, builder))
      .whereNotNull(field)
      .limit(searchOptions.limit)
      .pluck(field)
      .orderBy(field)
      .distinct();
  }

  private getTable(): Knex.QueryBuilder<any, any[]> {
    return this.db(this.tableName);
  }

  private addWhereClause(filter: string, builder: Knex.QueryBuilder<any, any[]>) {
    getWhereOptions(filter).forEach((filter) => {
      const field = filter.field;
      builder.andWhere((queryBuilder) => {
        if (filter.inFields.length > 0) {
          queryBuilder.whereIn(field, filter.inFields);
        }
        filter.likeFields.forEach((value) => queryBuilder.orWhereLike(field, value));
      });
    });
  }

  private getOrderBy(searchOptions: SortOptions): Order[] {
    let orderOptions: Order[] = getOrderByOptions(searchOptions);

    // add a default sort to the sort options
    if (this.defaultOrder) orderOptions.push(this.defaultOrder);

    return orderOptions;
  }
}
