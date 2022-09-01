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
  data: any;
}

/**
 * Sample Device Document
 * {
    "id": "85c3e6b7-9d9b-43f4-bbde-a2d569254c6a",
    "name": "acq-3",
    "make": "Acquisuite",
    "model": "Obvious AcquiSuite A88 12-1",
    "site": "CA-US-M3",
    "section": "FK",
    "lastPayload": "2022-03-16T05:18:26.871Z",
    "operational": "false",
    "serialNumber": "PI1H230ZQX",
    "firmware": "v3.4",
    "tags": string[],
    "points": Point[],
    "validation": Validation,
   }
 */
export interface Device {
  name: string;
  site: string;
  id?: string;
  make?: string;
  model?: string;
  section?: string;
  lastPayload?: string;
  operational?: string;
  serialNumber?: string;
  firmware?: string;
  tags?: string[];
  points?: Point[];
  validation?: Validation;
}

export class DeviceBuilder {
  private readonly _document: Device;

  constructor() {
    this._document = {
      name: '',
      site: '',
      tags: [],
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

  operational(operational: string): DeviceBuilder {
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
