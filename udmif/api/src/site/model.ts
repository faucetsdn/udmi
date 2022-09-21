import { SearchOptions } from '../common/model';

export interface SitesResponse {
  sites: Site[];
  totalCount: number;
  totalFilteredCount: number;
}

export interface SitesArgs {
  searchOptions: SearchOptions;
}

export interface SiteArgs {
  name: string;
}

export interface Site {
  name: string;
  validation?: any;
}
