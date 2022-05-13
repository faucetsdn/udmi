export interface DeviceKey {
  name: string;
  id: string;
}

export interface DeviceDocument {
  name: string;
  id: string;
  lastPayload?: string;
  make?: string;
  model?: string;
  operational?: string;
  serialNumber?: string;
  firmware?: string;
  section?: string;
  site?: string;
}
