export interface UdmiEvent {
  attributes: {
    deviceId: string;
    deviceRegistryId: string;
    deviceNumId?: string;
    subFolder?: string;
    subType?: string;
    projectId?: string;
  };
  data: any;
}

export interface PointsetEvent extends UdmiEvent {
  data: {
    // pointset
    points?: any;
  };
}

export interface SystemEvent extends UdmiEvent {
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
  };
}

export interface Status {
  message: string;
  category: string;
  level: number;
  timestamp: string;
  detail?: string;
}

export interface Error {
  message: string;
  category: string;
  level: number;
}

export interface PointSet {
  missing: string[];
  extra: string[];
}

export interface ValidationEvent extends UdmiEvent {
  data: {
    version: string;
    timestamp: string;
    last_updated: string;
    sub_folder?: string;
    sub_type?: string;
    status?: Status;
    errors?: Error[];
    pointset: PointSet[];
    summary: any;
    devices: any;
  };
}
