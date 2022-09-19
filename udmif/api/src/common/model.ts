export interface SearchOptions {
  batchSize?: number;
  offset?: number;
  sortOptions?: SortOptions;
  filter?: string;
}

export interface DistinctArgs {
  searchOptions: DistinctSearchOptions;
}

export interface ValidatedSearchOptions {
  batchSize?: number;
  offset: number;
  sortOptions?: SortOptions;
  filter?: string;
}

export interface DistinctSearchOptions {
  search?: string;
  limit?: number;
  filter?: string;
}

export interface ValidatedDistinctSearchOptions {
  search?: string;
  limit: number;
  filter?: string;
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
