import { UdmiMessage } from '../model/UdmiMessage';

export function createMessage(attributes: any, data: object = {}): UdmiMessage {
  return { attributes, data };
}

export function createMessageFromTypes(subFolder: string, subType: string, deviceId: string = 'AHU-1'): UdmiMessage {
  const defaultAttributes = {
    deviceId,
    deviceRegistryId: 'reg-1',
    subFolder,
    subType,
  };
  return { attributes: { ...defaultAttributes }, data: {} };
}
