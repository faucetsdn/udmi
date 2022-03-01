export interface DevicesResponse {
  devices: Device[];
  totalCount: number;
}

export interface SearchOptions {
  batchSize: number;
  offset?: number;
  sortOptions?: SortOptions;
  filter?: string;
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
  tags: string[];
}

export interface SortOptions {
  direction: SORT_DIRECTION;
  field: string;
}

export enum SORT_DIRECTION {
  DESC,
  ASC,
}
