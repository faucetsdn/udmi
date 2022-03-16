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
  tags: string[];
  points: string[];
}

export type Device = Partial<DeviceModel> | null;

export type DeviceResponse = {
  device: Device;
};

export type DeviceQueryResponse = DeviceResponse;

export type DeviceQueryVariables = {
  id: string;
};
