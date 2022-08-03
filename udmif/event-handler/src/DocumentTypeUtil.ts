export const POINTSET_SUB_FOLDER = 'pointset';
export const SYSTEM_SUB_FOLDER = 'system';
export const MODEL = 'model';
export const STATE = 'state';
export const CONFIG = 'config';

export function isPointset(message): boolean {
  return isSubFolder(message, POINTSET_SUB_FOLDER);
}

export function isSystem(message): boolean {
  return isSubFolder(message, SYSTEM_SUB_FOLDER);
}

export function isSubFolder(message, folderName: string): boolean {
  return message.attributes.subFolder === folderName;
}


