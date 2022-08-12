import {
  MODEL,
  CONFIG,
  POINTSET_SUB_FOLDER,
  STATE,
  EVENT,
  SYSTEM_SUB_FOLDER,
  VALIDATION_SUB_FOLDER,
  isSystemSubType,
  isPointsetSubType,
  isValidationSubType,
} from '../MessageUtils';
import { UdmiMessage } from '../model/UdmiMessage';

describe('DeviceTypeUtils.Ssystem', () => {
  const systemSubFolder = SYSTEM_SUB_FOLDER;

  test.each([
    [systemSubFolder, null, true],
    [systemSubFolder, STATE, true],
    [systemSubFolder, MODEL, true],
    [systemSubFolder, CONFIG, true],
    [systemSubFolder, EVENT, false],
  ])('is a system sub type %p %p', (subFolder: string, subType: string, expected: boolean) => {
    const inputMessage: UdmiMessage = createMessage(subFolder, subType);
    expect(isSystemSubType(inputMessage)).toEqual(expected);
  });
});

describe('DeviceDocumentFactory.PointSet', () => {
  const pointSubFolder = POINTSET_SUB_FOLDER;

  test.each([
    [pointSubFolder, null, true],
    [pointSubFolder, STATE, true],
    [pointSubFolder, MODEL, true],
    [pointSubFolder, CONFIG, true],
    [pointSubFolder, EVENT, false],
  ])('is a pointset sub type %p %p', (subFolder: string, subType: string, expected: boolean) => {
    const inputMessage: UdmiMessage = createMessage(subFolder, subType);
    expect(isPointsetSubType(inputMessage)).toEqual(expected);
  });
});

describe('DeviceTypeUtils.Validation', () => {
  const validationSubFolder = VALIDATION_SUB_FOLDER;

  test.each([
    [validationSubFolder, null, false],
    [validationSubFolder, STATE, false],
    [validationSubFolder, MODEL, false],
    [validationSubFolder, CONFIG, false],
    [validationSubFolder, EVENT, true],
  ])('is a pointset sub type %p %p', (subFolder: string, subType: string, expected: boolean) => {
    const inputMessage: UdmiMessage = createMessage(subFolder, subType);
    expect(isValidationSubType(inputMessage)).toEqual(expected);
  });
});

function createMessage(subFolder: string, subType?: string): UdmiMessage {
  const deviceId: string = 'name';
  const deviceRegistryId: string = 'id';

  const defaultAttributes = {
    deviceId,
    deviceRegistryId,
    subFolder,
    subType,
  };
  return { attributes: { ...defaultAttributes }, data: {} };
}
