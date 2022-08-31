export interface SiteKey {
  name: string;
}

export interface Site {
  name: string;
  lastMessage: any;
}

export interface SiteValidation {
  siteName: string;
  timestamp: Date;
  message: any;
}
