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
} from '../EventUtils';
import { UdmiEvent } from '../udmi/UdmiEvent';
import { createEventFromTypes } from './dataUtils';

describe('EventUtils.System', () => {
  const systemSubFolder = SYSTEM_SUB_FOLDER;

  test.each([
    [systemSubFolder, null, true],
    [systemSubFolder, STATE, true],
    [systemSubFolder, MODEL, true],
    [systemSubFolder, CONFIG, true],
    [systemSubFolder, EVENT, false],
  ])('is a system sub type %p %p', (subFolder: string, subType: string, expected: boolean) => {
    const inputEvent: UdmiEvent = createEventFromTypes(subFolder, subType);
    expect(isSystemSubType(inputEvent)).toEqual(expected);
  });
});

describe('EventUtils.PointSet', () => {
  const pointSubFolder = POINTSET_SUB_FOLDER;

  test.each([
    [pointSubFolder, null, true],
    [pointSubFolder, STATE, true],
    [pointSubFolder, MODEL, true],
    [pointSubFolder, CONFIG, true],
    [pointSubFolder, EVENT, false],
  ])('is a pointset sub type %p %p', (subFolder: string, subType: string, expected: boolean) => {
    const inputEvent: UdmiEvent = createEventFromTypes(subFolder, subType);
    expect(isPointsetSubType(inputEvent)).toEqual(expected);
  });
});

describe('EventUtils.Validation', () => {
  const validationSubFolder = VALIDATION_SUB_FOLDER;

  test.each([
    [validationSubFolder, null, true],
    [validationSubFolder, STATE, true],
    [validationSubFolder, MODEL, true],
    [validationSubFolder, CONFIG, true],
    [validationSubFolder, EVENT, true],
  ])('is a pointset sub type %p %p', (subFolder: string, subType: string, expected: boolean) => {
    const inputEvent: UdmiEvent = createEventFromTypes(subFolder, subType);
    expect(isValidationSubType(inputEvent)).toEqual(expected);
  });
});
