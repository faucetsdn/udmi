import { DeviceError } from '../device-errors/device-errors';

interface SiteModel {
  name: string;
  seenDevicesCount: number;
  totalDevicesCount: number;
  correctDevicesCount: number;
  correctDevicesPercent: number;
  missingDevicesCount: number;
  missingDevicesPercent: number;
  errorDevicesCount: number;
  errorDevicesPercent: number;
  extraDevicesCount: number;
  lastValidated: string;
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

export type SiteDetail = {
  value: keyof SiteModel;
  label: string;
};
