interface DeviceModel {
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
  points: string[];
  validation: string;
  level: number;
  message: string;
  details: string;
  lastSeen: string;
  state: string;
  errorsCount: number;
}

export type Device = Partial<DeviceModel>;

export type DeviceQueryResponse = {
  device?: Device;
};

export type DeviceQueryVariables = {
  id: string;
};

export type DeviceDetail = {
  value: keyof DeviceModel;
  label: string;
};
