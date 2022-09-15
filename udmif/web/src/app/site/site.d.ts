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
  totalDeviceErrorsCount: number;
  validation: string;
}

export type Site = Partial<SiteModel>;

export type SiteQueryResponse = {
  site?: Site;
};

export type SiteQueryVariables = {
  id: string;
};
