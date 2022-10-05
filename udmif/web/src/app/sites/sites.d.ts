import { Site } from '../site/site';

export interface SearchOptions {
  batchSize?: number;
  offset?: number;
  sortOptions?: SortOptions;
  filter?: string;
}

export interface SortOptions {
  direction?: 'ASC' | 'DESC';
  field: string;
}

export interface SitesResponse {
  sites?: Site[];
  totalCount: number;
  totalFilteredCount: number;
}

export type SitesQueryResponse = {
  sites?: SitesResponse;
};

export type SitesQueryVariables = {
  searchOptions: SearchOptions;
};

export interface CommonSearchOptions {
  search?: string;
  limit?: number;
  filter?: string;
}

export interface CommonSearchQueryVariables {
  searchOptions: CommonSearchOptions;
}

export type SiteNamesQueryResponse = {
  siteNames: string[];
};

export interface SiteNamesQueryVariables extends CommonSearchQueryVariables {}

export type SiteDistinctQueryResult = {
  values: string[];
};

export type SiteErrorSummaryItem = {
  count: number;
  message: string;
};
