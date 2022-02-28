export interface DevicesResponse {
  devices: Device[];
  totalCount: number;
}

export interface SearchOptions {
  batchSize: number;
  offset: number;
  sortOptions: SortOptions;
  filter: String;
}

export interface Device {
  id: string;
  name: string;
}

export interface SortOptions {
  direction: SORT_DIRECTION;
  field: String;
}

export enum SORT_DIRECTION {
  DESC,
  ASC,
}
