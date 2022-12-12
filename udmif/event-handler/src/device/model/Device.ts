import { Point } from './Point';
import { Validation } from '../../model/Validation';
import { InvalidEventError } from '../../InvalidEventError';

export interface DeviceKey {
  name: string;
  site: string;
}

export interface DeviceValidation {
  deviceKey: DeviceKey;
  timestamp: Date;
  message: any;
}

export interface Device {
  name: string;
  site: string;
  id: string;
  make?: string;
  model?: string;
  section?: string;
  lastPayload?: string;
  operational?: boolean;
  serialNumber?: string;
  firmware?: string;
  tags?: string[];
  points?: Point[] | string;
  validation?: Validation;
  lastTelemetryUpdated?: string;
  lastStateUpdated?: string;
  lastTelemetrySaved?: string;
  lastStateSaved?: string;
}

export class DeviceBuilder {
  private readonly _document: Device;

  constructor() {
    this._document = {
      name: '',
      site: '',
      id: '',
    };
  }

  id(id: string): DeviceBuilder {
    if (id) {
      this._document.id = id;
    }
    return this;
  }

  name(name: string): DeviceBuilder {
    if (name) {
      this._document.name = name;
    }
    return this;
  }

  operational(operational: boolean): DeviceBuilder {
    if (operational) {
      this._document.operational = operational;
    }
    return this;
  }

  serialNumber(serialNo: string): DeviceBuilder {
    if (serialNo) {
      this._document.serialNumber = serialNo;
    }
    return this;
  }

  make(make: string): DeviceBuilder {
    if (make) {
      this._document.make = make;
    }
    return this;
  }

  model(model: string): DeviceBuilder {
    if (model) {
      this._document.model = model;
    }
    return this;
  }

  firmware(firmware: string): DeviceBuilder {
    if (firmware) {
      this._document.firmware = firmware;
    }
    return this;
  }

  lastPayload(timestamp: string): DeviceBuilder {
    if (timestamp) {
      this._document.lastPayload = timestamp;
    }
    return this;
  }

  section(section: string): DeviceBuilder {
    if (section) {
      this._document.section = section;
    }
    return this;
  }

  site(site: string): DeviceBuilder {
    if (site) {
      this._document.site = site;
    }
    return this;
  }

  points(points: Point[]): DeviceBuilder {
    if (points) {
      this._document.points = points;
    }
    return this;
  }

  validation(validation: Validation): DeviceBuilder {
    if (validation) {
      this._document.validation = validation;
    }
    return this;
  }

  lastStateUpdated(timestamp: string): DeviceBuilder {
    if (timestamp) {
      this._document.lastStateUpdated = timestamp;
    }
    return this;
  }

  lastStateSaved(timestamp: string): DeviceBuilder {
    if (timestamp) {
      this._document.lastStateSaved = timestamp;
    }
    return this;
  }

  lastTelemetryUpdated(timestamp: string): DeviceBuilder {
    if (timestamp) {
      this._document.lastTelemetryUpdated = timestamp;
    }
    return this;
  }

  lastTelemetrySaved(timestamp: string): DeviceBuilder {
    if (timestamp) {
      this._document.lastTelemetrySaved = timestamp;
    }
    return this;
  }

  build(): Device {
    if (this._document.site === '') {
      throw new InvalidEventError('Device site can not be empty');
    }
    if (this._document.name === '') {
      throw new InvalidEventError('Device name can not be empty');
    }
    return this._document;
  }
}

export const PRIMARY_KEYS: string[] = ['name', 'site'];
