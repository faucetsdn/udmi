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

export type DevicesResponse = {
  devices: Device[];
  totalCount: number;
  totalFilteredCount: number;
};

export type DevicesQueryResponse = {
  devices: DevicesResponse;
};

export type DevicesQueryVariables = {
  searchOptions: SearchOptions;
};
