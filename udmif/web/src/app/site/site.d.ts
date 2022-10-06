import { DeviceError } from '../device-errors/device-errors';

interface SiteModel {
  name: string;
  totalDevicesCount: number;
  correctDevicesCount: number;
  missingDevicesCount: number;
  errorDevicesCount: number;
  extraDevicesCount: number;
  lastValidated: string;
  percentValidated: number;
  deviceErrors: DeviceError[];
  totalDeviceErrorsCount: number;
  validation: string;
}

export type Site = Partial<SiteModel>;

export type SiteQueryResponse = {
  site?: Site;
};

export type SiteQueryVariables = {
  name: string;
};
