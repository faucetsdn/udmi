import { Point } from './Point';

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
    "points": Point[]
   }
 */
export interface DeviceDocument {
  id: string;
  name: string;
  make?: string;
  model?: string;
  site?: string;
  section?: string;
  lastPayload?: string;
  operational?: string;
  serialNumber?: string;
  firmware?: string;
  tags?: string[];
  points?: Point[];
}

export class DeviceDocumentBuilder {
  private readonly _document: DeviceDocument;

  constructor() {
    this._document = {
      id: '',
      name: '',
      tags: [],
    };
  }

  id(id: string): DeviceDocumentBuilder {
    if (id) {
      this._document.id = id;
    }
    return this;
  }

  name(name: string): DeviceDocumentBuilder {
    if (name) {
      this._document.name = name;
    }
    return this;
  }

  operational(operational: string): DeviceDocumentBuilder {
    if (operational) {
      this._document.operational = operational;
    }
    return this;
  }

  serialNumber(serialNo: string): DeviceDocumentBuilder {
    if (serialNo) {
      this._document.serialNumber = serialNo;
    }
    return this;
  }

  make(make: string): DeviceDocumentBuilder {
    if (make) {
      this._document.make = make;
    }
    return this;
  }

  model(model: string): DeviceDocumentBuilder {
    if (model) {
      this._document.model = model;
    }
    return this;
  }

  firmware(firmware: string): DeviceDocumentBuilder {
    if (firmware) {
      this._document.firmware = firmware;
    }
    return this;
  }

  lastPayload(timestamp: string): DeviceDocumentBuilder {
    if (timestamp) {
      this._document.lastPayload = timestamp;
    }
    return this;
  }

  section(section: string): DeviceDocumentBuilder {
    if (section) {
      this._document.section = section;
    }
    return this;
  }

  site(site: string): DeviceDocumentBuilder {
    if (site) {
      this._document.site = site;
    }
    return this;
  }

  points(points: Point[]): DeviceDocumentBuilder {
    if (points) {
      this._document.points = points;
    }
    return this;
  }

  build(): DeviceDocument {
    if (this._document.id === '') {
      throw new Error('Point id can not be empty');
    }
    if (this._document.name === '') {
      throw new Error('Point name can not be empty');
    }
    return this._document;
  }
}
