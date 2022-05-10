import { Device } from '../device/device';

export interface SearchOptions {
  batchSize: number;
  offset?: number;
  sortOptions?: SortOptions;
  filter?: string;
}

export interface SortOptions {
  direction?: 'ASC' | 'DESC';
  field: string;
}

export type DevicesQueryResponse = {
  devices: {
    devices: Device[] | null;
    totalCount: number;
    totalFilteredCount: number;
  } | null;
};

export type DevicesQueryVariables = {
  searchOptions: SearchOptions;
};

export type DeviceNamesQueryResponse = {
  deviceNames: string[];
};

export type DeviceMakesQueryResponse = {
  deviceMakes: string[];
};

export type DeviceModelsQueryResponse = {
  deviceModels: string[];
};

export type DeviceSitesQueryResponse = {
  deviceSites: string[];
};

export type DeviceSectionsQueryResponse = {
  deviceSections: string[];
};

export type DeviceDistinctQueryVariables = {
  term?: string;
  limit?: number;
};

export type DeviceDistinctQueryResult = {
  values: string[];
};
