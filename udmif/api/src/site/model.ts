export interface SitesResponse {
  sites: Site[];
  totalCount: number;
  totalFilteredCount: number;
}

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

export interface ValidatedSiteNamesSearchOptions extends ValidatedCommonSearchOptions {}

export interface CommonSearchOptions {
  search?: string;
  limit?: number;
}

export interface SiteNamesSearchOptions extends CommonSearchOptions {}

export interface Site {
  id: string;
  name: string;
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
