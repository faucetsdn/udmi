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
  id: string;
}

export interface Site {
  id: string;
  name: string;
  validation?: any;
}
