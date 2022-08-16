import { Error, Status } from './UdmiMessage';
import { InvalidMessageError } from '../InvalidMessageError';

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
  timestamp: string;
  version: string;
  category?: string;
  message?: string;
  detail?: string;
  errors?: Error[];
  status?: Status;
}

export class ValidationBuilder {
  private _timestamp: string = null;
  private _version: string = null;
  private _category: string = null;
  private _message: string = null;
  private _detail: string = null;
  private _errors: Error[] = null;
  private _status: Status = null;

  timestamp(timestamp: string): ValidationBuilder {
    if (!timestamp) {
      throw new InvalidMessageError('Validation timestamp can not be empty');
    }
    this._timestamp = timestamp;
    return this;
  }

  version(version: string): ValidationBuilder {
    if (!version) {
      throw new InvalidMessageError('Validation version can not be empty');
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
      version: this._version,
      category: this._category,
      status: this._status,
      detail: this._detail,
      message: this._message,
      errors: this._errors,
    };
  }
}
