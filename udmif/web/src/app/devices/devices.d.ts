import { Device } from '../device/device';

export interface SearchOptions {
  batchSize: number;
  offset?: number;
  sortOptions?: SortOptions;
  filter?: string;
  uniqueBy?: string;
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
