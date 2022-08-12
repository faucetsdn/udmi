export interface Error {
  message: string;
  category: string;
  level: number;
}

export interface Status {
  message: string;
  detail: string;
  category: string;
}

export interface UdmiMessage {
  attributes: {
    deviceId: string;
    deviceRegistryId: string;
    deviceNumId?: string;
    subFolder?: string;
    subType?: string;
    projectId?: string;
  };
  data: {
    // system
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
    // pointset
    points?: any;
    // validation
    version?: string;
    sub_folder?: string;
    sub_type?: string;
    status?: Status;
    errors?: Error[];
  };
}
