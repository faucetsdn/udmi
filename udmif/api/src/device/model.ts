export interface DevicesResponse {
  devices: Device[];
  totalCount: number;
  totalFilteredCount: number;
}

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

export interface ValidatedDeviceNamesSearchOptions extends ValidatedCommonSearchOptions {}
export interface ValidatedDeviceMakesSearchOptions extends ValidatedCommonSearchOptions {}
export interface ValidatedDeviceModelsSearchOptions extends ValidatedCommonSearchOptions {}
export interface ValidatedSiteNamesSearchOptions extends ValidatedCommonSearchOptions {}
export interface ValidatedSectionsSearchOptions extends ValidatedCommonSearchOptions {}

export interface CommonSearchOptions {
  search?: string;
  limit?: number;
}

export interface DeviceNamesSearchOptions extends CommonSearchOptions {}
export interface DeviceMakesSearchOptions extends CommonSearchOptions {}
export interface DeviceModelsSearchOptions extends CommonSearchOptions {}
export interface SiteNamesSearchOptions extends CommonSearchOptions {}
export interface SectionsSearchOptions extends CommonSearchOptions {}

export interface Point {
  id: string;
  name: string;
  value: string;
  units: string;
  state: string;
}

export interface Device {
  id: string;
  name: string;
  make: string;
  model: string;
  site: string;
  section: string;
  lastPayload: string;
  operational: boolean;
  firmware: string;
  serialNumber: string;
  tags?: string[];
  points?: Point[];
}

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
