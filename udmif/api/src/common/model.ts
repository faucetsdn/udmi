export interface SearchOptions {
  batchSize: number;
  offset?: number;
  sortOptions?: SortOptions;
  filter?: string;
}

export interface ValidatedCommonSearchOptions {
  search?: string;
  limit: number;
}

export interface DistinctSearchOptions extends CommonSearchOptions {}
export interface ValidatedDistinctSearchOptions extends ValidatedCommonSearchOptions {}

export interface CommonSearchOptions {
  search?: string;
  limit?: number;
}

export interface SortOptions {
  direction: SORT_DIRECTION;
  field: string;
}

export enum SORT_DIRECTION {
  ASC,
  DESC,
}

export interface Filter {
  field: string;
  operator: string;
  value: string;
}
