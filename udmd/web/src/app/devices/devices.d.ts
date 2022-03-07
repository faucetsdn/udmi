export interface Device {
  id: string;
  name: string;
  make?: string;
  model?: string;
  site?: string;
  section?: string;
  lastPayload?: string;
  operational?: boolean;
  tags: string[];
}

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
};

export type DevicesQueryResponse = {
  devices: DevicesResponse;
};

export type DevicesQueryVariables = {
  searchOptions: SearchOptions;
};
