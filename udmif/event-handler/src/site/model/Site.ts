export interface SiteKey {
  name: string;
}

export interface Site {
  name: string;
  validation?: any;
}

export interface SiteValidation {
  siteName: string;
  timestamp: Date;
  data: any;
}

export const PRIMARY_KEYS: string[] = ['name'];
