import { InvalidMessageError } from '../../InvalidMessageError';

/**
 * Sample point.
 * {
      "id": "ZT-1",
      "name": "Zone Temperature",
      "value": "78.12",
      "units": "℉",
      "meta": {
        "code": "zone_temperature",
        "units": "℉"
      },
      "state": "Applied"
    }
 */
export interface Point {
  id: string;
  name: string;
  value?: string;
  units?: string;
  meta?: {
    code?: string;
    units?: string;
  };
  state?: string;
}

export class PointBuilder {
  private readonly _document: Point;

  constructor() {
    this._document = {
      id: '',
      name: '',
      state: '',
    };
  }

  id(id: string): PointBuilder {
    if (id) {
      this._document.id = id;
    }
    return this;
  }

  /* we are using the code in the pointset message with no translation as the name*/
  name(name: string): PointBuilder {
    if (name) {
      this._document.name = name;
    }
    return this;
  }

  value(value: string): PointBuilder {
    if (value) {
      this._document.value = value;
    }
    return this;
  }

  units(unit: string): PointBuilder {
    if (unit) {
      this._document.units = unit;
    }
    return this;
  }

  state(state: string): PointBuilder {
    if (state) {
      this._document.state = state;
    }
    return this;
  }

  metaCode(code: string): PointBuilder {
    this.initMeta();

    if (code) {
      this._document.meta.code = code;
    }
    return this;
  }

  metaUnit(units: string): PointBuilder {
    this.initMeta();

    if (units) {
      this._document.meta.units = units;
    }
    return this;
  }

  private initMeta() {
    if (!this._document.meta) {
      this._document.meta = {};
    }
  }

  build(): Point {
    if (this._document.id === '') {
      throw new InvalidMessageError('Point id can not be empty');
    }
    if (this._document.name === '') {
      throw new InvalidMessageError('Point name can not be empty');
    }
    return this._document;
  }
}
