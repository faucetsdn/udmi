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

export interface DevicesResponse {
  devices?: Device[];
  totalCount: number;
  totalFilteredCount: number;
}

export type DevicesQueryResponse = {
  devices?: DevicesResponse;
};

export type DevicesQueryVariables = {
  searchOptions: SearchOptions;
};

export interface CommonSearchOptions {
  search?: string;
  limit?: number;
}

export interface CommonSearchQueryVariables {
  searchOptions: CommonSearchOptions;
}

export type DeviceNamesQueryResponse = {
  deviceNames: string[];
};

export interface DeviceNamesQueryVariables extends CommonSearchQueryVariables {}

export type DeviceMakesQueryResponse = {
  deviceMakes: string[];
};

export interface DeviceMakesQueryVariables extends CommonSearchQueryVariables {}

export type DeviceModelsQueryResponse = {
  deviceModels: string[];
};

export interface DeviceModelsQueryVariables extends CommonSearchQueryVariables {}

export type DeviceSitesQueryResponse = {
  siteNames: string[];
};

export interface DeviceSitesQueryVariables extends CommonSearchQueryVariables {}

export type DeviceSectionsQueryResponse = {
  sections: string[];
};

export interface DeviceSectionsQueryVariables extends CommonSearchQueryVariables {}

export type DeviceDistinctQueryResult = {
  values: string[];
};
