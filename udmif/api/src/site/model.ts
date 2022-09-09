import { DistinctSearchOptions, SearchOptions } from '../common/model';

export interface SitesResponse {
  sites: Site[];
  totalCount: number;
  totalFilteredCount: number;
}

export interface SiteNamesArgs {
  searchOptions: DistinctSearchOptions;
}
export interface SitesArgs {
  searchOptions: SearchOptions;
}

export interface Site {
  id: string;
  name: string;
  validation?: any;
}
