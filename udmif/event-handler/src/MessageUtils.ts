export const POINTSET_SUB_FOLDER = 'pointset';
export const SYSTEM_SUB_FOLDER = 'system';
export const VALIDATION_SUB_FOLDER = 'validation';
export const MODEL = 'model';
export const STATE = 'state';
export const CONFIG = 'config';
export const EVENT = 'event';

export function isPointsetSubType(message): boolean {
  return (
    isSubFolder(message, POINTSET_SUB_FOLDER) &&
    (!message.attributes.subType ||
      isSubType(message, MODEL) ||
      isSubType(message, STATE) ||
      isSubType(message, CONFIG))
  );
}

export function isSystemSubType(message): boolean {
  return (
    isSubFolder(message, SYSTEM_SUB_FOLDER) &&
    (!message.attributes.subType ||
      isSubType(message, MODEL) ||
      isSubType(message, STATE) ||
      isSubType(message, CONFIG))
  );
}

export function isValidationSubType(message): boolean {
  return isSubFolder(message, VALIDATION_SUB_FOLDER);
}

export function isSubFolder(message, folderName: string): boolean {
  return message.attributes.subFolder === folderName;
}

export function isSubType(message, typeName: string): boolean {
  return message.attributes.subType === typeName;
}
