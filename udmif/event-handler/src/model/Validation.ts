import { Error, PointSet, Status, Summary } from '../udmi/UdmiEvent';
import { InvalidEventError } from '../InvalidEventError';

/**
 * Sample incoming validation.
 * {
        "timestamp":"2022-08-03T17:28:49Z",
        "version":"1.3.14",
        "sub_folder":"validation",
        "sub_type":"event",
        "status":{
            "message":"Multiple validation errors",
            "detail":"While converting to json node: 2 schema violations found; While converting to json node: 1 schema violations found",
            "category":"validation.error.multiple"
        },
        "errors":[
            {"message":"While converting to json node: 2 schema violations found","category":"validation.error.simple","level":500},
            {"message":"While converting to json node: 1 schema violations found","category":"validation.error.simple","level":500}
        ]
    },
 */
export interface Validation {
  version: string;
  timestamp: string;
  last_updated: string;
  category?: string;
  message?: string;
  detail?: string;
  devices?: any;
  errors?: Error[];
  pointset?: PointSet[];
  summary?: Summary;
  status?: Status;
}

export class ValidationBuilder {
  private _version: string = null;
  private _timestamp: string = null;
  private _last_updated: string = null;
  private _category: string = null;
  private _message: string = null;
  private _detail: string = null;
  private _errors: Error[] = null;
  private _status: Status = null;

  timestamp(timestamp: string): ValidationBuilder {
    if (!timestamp) {
      throw new InvalidEventError('Validation timestamp can not be empty');
    }
    this._timestamp = timestamp;
    return this;
  }

  last_updated(last_updated: string): ValidationBuilder {
    if (last_updated) {
      this._last_updated = last_updated;
    }
    return this;
  }

  version(version: string): ValidationBuilder {
    if (!version) {
      throw new InvalidEventError('Validation version can not be empty');
    }
    this._version = version;
    return this;
  }

  category(category: string): ValidationBuilder {
    if (category) {
      this._category = category;
    }
    return this;
  }

  message(message: string): ValidationBuilder {
    if (message) {
      this._message = message;
    }
    return this;
  }

  errors(errors: Error[]): ValidationBuilder {
    if (errors) {
      this._errors = errors;
    }
    return this;
  }

  status(status: Status): ValidationBuilder {
    if (status) {
      this._status = status;
    }
    return this;
  }

  detail(detail: string): ValidationBuilder {
    if (detail) {
      this._detail = detail;
    }
    return this;
  }

  build(): Validation {
    this.timestamp(this._timestamp);
    this.version(this._version);

    return {
      timestamp: this._timestamp,
      last_updated: this._last_updated,
      version: this._version,
      category: this._category,
      status: this._status,
      detail: this._detail,
      message: this._message,
      errors: this._errors,
    };
  }
}
