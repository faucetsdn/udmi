import { UdmiMessage } from './model/UdmiMessage';

export const POINTSET_SUB_FOLDER = 'pointset';
export const SYSTEM_SUB_FOLDER = 'system';
export const VALIDATION_SUB_FOLDER = 'validation';
export const MODEL = 'model';
export const STATE = 'state';
export const CONFIG = 'config';
export const EVENT = 'event';

export function isPointsetSubType(message: UdmiMessage): boolean {
  return (
    isSubFolder(message, POINTSET_SUB_FOLDER) &&
    (!message.attributes.subType ||
      isSubType(message, MODEL) ||
      isSubType(message, STATE) ||
      isSubType(message, CONFIG))
  );
}

export function isSystemSubType(message: UdmiMessage): boolean {
  return (
    isSubFolder(message, SYSTEM_SUB_FOLDER) &&
    (!message.attributes.subType ||
      isSubType(message, MODEL) ||
      isSubType(message, STATE) ||
      isSubType(message, CONFIG))
  );
}

export function isValidationSubType(message: UdmiMessage): boolean {
  return isSubFolder(message, VALIDATION_SUB_FOLDER);
}

export function isSubFolder(message: UdmiMessage, folderName: string): boolean {
  return message.attributes.subFolder === folderName;
}

export function isSubType(message: UdmiMessage, typeName: string): boolean {
  return message.attributes.subType === typeName;
}
