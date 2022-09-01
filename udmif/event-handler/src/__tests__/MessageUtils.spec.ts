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
import { createMessageFromTypes } from './dataUtils';

describe('MessageUtils.System', () => {
  const systemSubFolder = SYSTEM_SUB_FOLDER;

  test.each([
    [systemSubFolder, null, true],
    [systemSubFolder, STATE, true],
    [systemSubFolder, MODEL, true],
    [systemSubFolder, CONFIG, true],
    [systemSubFolder, EVENT, false],
  ])('is a system sub type %p %p', (subFolder: string, subType: string, expected: boolean) => {
    const inputMessage: UdmiMessage = createMessageFromTypes(subFolder, subType);
    expect(isSystemSubType(inputMessage)).toEqual(expected);
  });
});

describe('MessageUtils.PointSet', () => {
  const pointSubFolder = POINTSET_SUB_FOLDER;

  test.each([
    [pointSubFolder, null, true],
    [pointSubFolder, STATE, true],
    [pointSubFolder, MODEL, true],
    [pointSubFolder, CONFIG, true],
    [pointSubFolder, EVENT, false],
  ])('is a pointset sub type %p %p', (subFolder: string, subType: string, expected: boolean) => {
    const inputMessage: UdmiMessage = createMessageFromTypes(subFolder, subType);
    expect(isPointsetSubType(inputMessage)).toEqual(expected);
  });
});

describe('MessageUtils.Validation', () => {
  const validationSubFolder = VALIDATION_SUB_FOLDER;

  test.each([
    [validationSubFolder, null, true],
    [validationSubFolder, STATE, true],
    [validationSubFolder, MODEL, true],
    [validationSubFolder, CONFIG, true],
    [validationSubFolder, EVENT, true],
  ])('is a pointset sub type %p %p', (subFolder: string, subType: string, expected: boolean) => {
    const inputMessage: UdmiMessage = createMessageFromTypes(subFolder, subType);
    expect(isValidationSubType(inputMessage)).toEqual(expected);
  });
});
