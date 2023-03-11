import { ValidatedDistinctSearchOptions, ValidatedSearchOptions } from '../common/model';

export interface DAO<Type> {
  getAll(searchOptions: ValidatedSearchOptions): Promise<Type[]>;
  getAllIn(field: string, values: readonly string[]): Promise<Type[]>;
  getOne(filterQuery: any): Promise<Type>;
  getFilteredCount(searchOptions: ValidatedSearchOptions): Promise<number>;
  getCount(): Promise<number>;
  getDistinct(field: string, searchOptions: ValidatedDistinctSearchOptions): Promise<string[]>;
}
