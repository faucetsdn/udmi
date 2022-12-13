import { ValidatedDistinctSearchOptions, ValidatedSearchOptions } from '../common/model';

export interface DAO<Type> {
  getAll(searchOptions: ValidatedSearchOptions): Promise<Type[] | null>;
  getOne(filterQuery: any): Promise<Type | null>;
  getFilteredCount(searchOptions: ValidatedSearchOptions): Promise<number>;
  getCount(): Promise<number>;
  getDistinct(field: string, searchOptions: ValidatedDistinctSearchOptions): Promise<string[] | null>;
}
