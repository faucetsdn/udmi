export interface DeviceKey {
    name: string;
    id: string;
  }
  
  export interface DeviceDocument {
    name: string;
    id: string;
    lastPayload?: string;
    make?: string;
    model?: string;
    operational?: string;
    serialNumber?: string;
    firmware?: string;
    section?: string;
    site?: string;
  }
  
  export interface UdmiMessage {
    attributes: {
      deviceId: string;
      deviceNumId: string;
    };
    data: {
      hardware?: {
        make: string;
        model: string;
      };
      location?: {
        section: string;
        site: string;
      };
      software?: any;
      operational?: string;
      serial_no?: string;
      timestamp?: string;
    };
  }
  