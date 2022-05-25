export const POINTSET_SUB_FOLDER = 'pointset';
export const SYSTEM_SUB_FOLDER = 'system';
export const MODEL = 'model';
export const STATE = 'state';
export const CONFIG = 'config';

export function isPointset(message): boolean {
  return isSubFolder(message, POINTSET_SUB_FOLDER) && !message.attributes.subType;
}

export function isPointsetModel(message): boolean {
  return isSubFolder(message, POINTSET_SUB_FOLDER) && isSubType(message, MODEL);
}

export function isPointsetState(message): boolean {
  return isSubFolder(message, POINTSET_SUB_FOLDER) && isSubType(message, STATE);
}

export function isPointsetConfig(message): boolean {
  return isSubFolder(message, POINTSET_SUB_FOLDER) && isSubType(message, CONFIG);
}

export function isSystemState(message): boolean {
  return isSubFolder(message, SYSTEM_SUB_FOLDER) && isSubType(message, STATE);
}

export function isSystemModel(message): boolean {
  return isSubFolder(message, SYSTEM_SUB_FOLDER) && isSubType(message, MODEL);
}

export function isSubFolder(message, folderName: string): boolean {
  return message.attributes.subFolder === folderName;
}

export function isSubType(message, type: string): boolean {
  return message.attributes.subType === type;
}
