export interface Site {
  name: string;
  validation?: any;
}

export interface SiteValidation {
  siteName: string;
  timestamp: Date;
  message: any;
}

export const PRIMARY_KEYS: string[] = ['name'];
