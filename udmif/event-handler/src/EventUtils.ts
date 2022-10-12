import { UdmiEvent } from './model/UdmiEvent';

export const POINTSET_SUB_FOLDER = 'pointset';
export const SYSTEM_SUB_FOLDER = 'system';
export const VALIDATION_SUB_FOLDER = 'validation';
export const UPDATE_SUB_FOLDER = 'update';
export const MODEL = 'model';
export const STATE = 'state';
export const CONFIG = 'config';
export const EVENT = 'event';

export function isPointsetSubType(event: UdmiEvent): boolean {
  return (
    isSubFolder(event, POINTSET_SUB_FOLDER) &&
    (!event.attributes.subType || isSubType(event, MODEL) || isSubType(event, STATE) || isSubType(event, CONFIG))
  );
}

export function isSystemSubType(event: UdmiEvent): boolean {
  return (
    isSubFolder(event, SYSTEM_SUB_FOLDER) &&
    (!event.attributes.subType || isSubType(event, MODEL) || isSubType(event, STATE) || isSubType(event, CONFIG))
  );
}

export function isValidationSubType(event: UdmiEvent): boolean {
  return isSubFolder(event, VALIDATION_SUB_FOLDER);
}

export function isSubFolder(event: UdmiEvent, folderName: string): boolean {
  return event.attributes.subFolder === folderName;
}

export function isSubType(event: UdmiEvent, typeName: string): boolean {
  return event.attributes.subType === typeName;
}
