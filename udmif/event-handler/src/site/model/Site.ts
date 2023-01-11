import { Validation } from '../../model/Validation';

export interface Site {
  name: string;
  validation?: Validation;
}

export interface SiteValidation {
  siteName: string;
  timestamp: Date;
  message: Validation;
}

export const PRIMARY_KEYS: string[] = ['name'];
