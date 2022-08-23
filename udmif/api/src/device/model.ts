export interface DevicesResponse {
  devices: Device[];
  totalCount: number;
  totalFilteredCount: number;
}

export interface Point {
  id: string;
  name: string;
  value: string;
  units: string;
  state: string;
}

export interface Device {
  id?: string;
  name: string;
  make?: string;
  model?: string;
  site: string;
  section?: string;
  lastPayload?: string;
  operational?: boolean;
  firmware?: string;
  serialNumber?: string;
  tags?: string[];
  points?: Point[];
}
