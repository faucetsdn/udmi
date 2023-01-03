import { SearchOptions } from '../common/model';

export interface DevicesResponse {
  devices: Device[];
  totalCount: number;
  totalFilteredCount: number;
}

export interface DevicesArgs {
  searchOptions: SearchOptions;
}

export interface DeviceArgs {
  id: string;
}

export interface PointsArgs {
  deviceId: string;
}

export interface Point {
  id: string;
  name: string;
  value: string;
  units: string;
  state: string;
}

export interface Device {
  uuid?: string;
  id: string;
  name: string;
  make?: string;
  model?: string;
  site: string;
  section?: string;
  lastPayload?: string;
  operational?: boolean;
  firmware?: string;
  serialNumber?: string;
  points?: Point[];
  validation?: any;
  lastStateUpdated?: string;
  lastStateSaved?: string;
  lastTelemetryUpdated?: string;
  lastTelemetrySaved?: string;
}
