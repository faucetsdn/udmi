interface SiteModel {
  id: string;
  name: string;
  totalDevicesCount: number;
  correctDevicesCount: number;
  missingDevicesCount: number;
  errorDevicesCount: number;
  extraDevicesCount: number;
  lastValidated: string;
  percentValidated: number;
  totalDeviceErrorsCount: number
}

export type Site = Partial<SiteModel>;
