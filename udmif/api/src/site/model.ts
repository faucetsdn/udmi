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
  validation?: SiteValidation;
}

export type Status = {
  message: string;
  detail?: string;
  category: string;
  timestamp: string;
  level: number;
};

export type SiteValidation = {
  timestamp?: string;
  version?: string;
  last_updated: string;
  status?: Status;
  summary: {
    correct_devices?: string[];
    extra_devices?: string[];
    missing_devices?: string[];
    error_devices?: string[];
  };
  devices: any; //TODO::
};
