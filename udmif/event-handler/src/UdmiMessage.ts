export interface UdmiMessage {
  attributes: {
    deviceId: string;
    deviceNumId: string;
    subFolder?: string;
    subType?: string;
    deviceRegistryId?: string;
    projectId?: string;
  };
  data: {
    hardware?: {
      make: string;
      model: string;
    };
    location?: {
      section: string;
      site: string;
      position?: {
        x?: number;
        y?: number;
      };
    };
    physical_tag?: {
      asset?: {
        guid?: string;
        site?: string;
        name?: string;
      };
    };
    software?: {
      firmware?: string;
    };
    operational?: string;
    serial_no?: string;
    timestamp?: string;
    points?: any;
  };
}
