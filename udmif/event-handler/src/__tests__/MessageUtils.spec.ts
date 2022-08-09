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
import { UdmiMessage } from '../UdmiMessage';


describe('DeviceTypeUtils.Ssystem', () => {

  const subFolder = SYSTEM_SUB_FOLDER;

  test.each([
    [subFolder, null, true],
    [subFolder, STATE, true],
    [subFolder, MODEL, true],
    [subFolder, CONFIG, true],
    [subFolder, EVENT, false]
  ])('is a system sub type %p %p', (subFolder: string, subType: string, expected: boolean) => {
    const inputMessage: UdmiMessage = createMessage(subFolder, subType);
    expect(isSystemSubType(inputMessage)).toEqual(expected);
  });
});

describe('DeviceDocumentFactory.PointSet', () => {

  const subFolder = POINTSET_SUB_FOLDER;

  test.each([
    [subFolder, null, true],
    [subFolder, STATE, true],
    [subFolder, MODEL, true],
    [subFolder, CONFIG, true],
    [subFolder, EVENT, false],
  ])('is a pointset sub type %p %p', (subFolder: string, subType: string, expected: boolean) => {
    const inputMessage: UdmiMessage = createMessage(subFolder, subType);
    expect(isPointsetSubType(inputMessage)).toEqual(expected);
  });

});

describe('DeviceTypeUtils.Validation', () => {

  const subFolder = VALIDATION_SUB_FOLDER;

  test.each([
    [subFolder, null, false],
    [subFolder, STATE, false],
    [subFolder, MODEL, false],
    [subFolder, CONFIG, false],
    [subFolder, EVENT, true],
  ])('is a pointset sub type %p %p', (subFolder: string, subType: string, expected: boolean) => {
    const inputMessage: UdmiMessage = createMessage(subFolder, subType);
    expect(isValidationSubType(inputMessage)).toEqual(expected);
  });

});

function createMessage(subFolder: string, subType?: string): UdmiMessage {

  const deviceId: string = 'name';
  const deviceNumId: string = 'id';

  const defaultAttributes = {
    deviceId,
    deviceNumId,
    subFolder,
    subType
  };
  return { attributes: { ...defaultAttributes }, data: {} };
}