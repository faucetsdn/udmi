import { Error } from "./UdmiMessage";
import { InvalidMessageError } from "../InvalidMessageError";

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
    category: string;
    message: string;
    timestamp: string;
    detail?: string;
    errors?: Error[];
}

export class ValidationBuilder {
    private readonly _document: Validation;

    constructor() {
        this._document = {
            category: '',
            message: '',
            timestamp: '',
            detail: '',
            errors: [],
        };
    }

    category(category: string): ValidationBuilder {
        if (!category) {
            throw new InvalidMessageError('Validation category can not be empty');
        }

        this._document.category = category;
        return this;
    }

    message(message: string): ValidationBuilder {
        if (!message) {
            throw new InvalidMessageError('Validation message can not be empty');
        }

        this._document.message = message;
        return this;
    }

    timestamp(timestamp: string): ValidationBuilder {
        if (!timestamp) {
            throw new InvalidMessageError('Validation timestamp can not be empty');
        }
        this._document.timestamp = timestamp;
        return this;
    }

    errors(errors: Error[]): ValidationBuilder {
        if (!errors) {
            throw new InvalidMessageError('Validation errors can not be empty');
        }

        this._document.errors = errors;
        return this;
    }

    detail(detail: string): ValidationBuilder {
        if (detail) {
            this._document.detail = detail;
        }
        return this;
    }

    build(): Validation {
        return this._document;
    }
}
