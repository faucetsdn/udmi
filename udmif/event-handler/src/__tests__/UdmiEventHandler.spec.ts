import { UdmiEvent } from '../udmi/UdmiEvent';
import UdmiEventHandler, { VALIDATOR_ID } from '../udmi/UdmiEventHandler';
import {
  CONFIG,
  EVENT,
  MODEL,
  POINTSET_SUB_FOLDER,
  STATE,
  SYSTEM_SUB_FOLDER,
  UPDATE_SUB_FOLDER,
  VALIDATION_SUB_FOLDER,
} from '../EventUtils';
import { Handler } from '../Handler';
import { createEvent, createEventFromTypes } from './dataUtils';

const AHU_ID: string = 'AHU-1';
const SITE_ID: string = 'site-1';

describe('UdmiEventHandler', () => {
  const mockDeviceHandle = jest.fn();
  const mockSiteHandle = jest.fn();

  let udmiEventHandler: UdmiEventHandler;

  beforeEach(() => {
    jest.clearAllMocks();
    const mockDeviceHandler: Handler = { handle: mockDeviceHandle };
    const mockSiteHandler: Handler = { handle: mockSiteHandle };
    udmiEventHandler = new UdmiEventHandler(mockDeviceHandler, mockSiteHandler);
  });

  test('Unhandled Message Types are logged', () => {
    const event: UdmiEvent = createEvent(
      {
        deviceId: AHU_ID,
        deviceRegistryId: SITE_ID,
        subFolder: 'random',
        subType: MODEL,
      },
      {}
    );

    // arrange
    jest.spyOn(global.console, 'warn');

    // act
    udmiEventHandler.handleUdmiEvent(event);

    // assert
    expect(console.warn).toHaveBeenCalledWith('Skipping UDMI message: ' + JSON.stringify(event));
  });

  test.each([
    [VALIDATION_SUB_FOLDER, STATE],
    [UPDATE_SUB_FOLDER, STATE],
  ])('Handle site messages', (subFolder: string, subType: string) => {
    // arrange
    const event: UdmiEvent = createEvent(
      {
        deviceId: VALIDATOR_ID,
        deviceRegistryId: SITE_ID,
        subFolder: subFolder,
        subType,
      },
      {}
    );

    // act
    udmiEventHandler.handleUdmiEvent(event);

    // assert
    expect(mockSiteHandle).toHaveBeenCalled();
  });

  test.each([
    [SYSTEM_SUB_FOLDER, STATE],
    [SYSTEM_SUB_FOLDER, MODEL],
    [SYSTEM_SUB_FOLDER, CONFIG],
    [POINTSET_SUB_FOLDER, STATE],
    [POINTSET_SUB_FOLDER, MODEL],
    [POINTSET_SUB_FOLDER, CONFIG],
    [VALIDATION_SUB_FOLDER, EVENT],
    [VALIDATION_SUB_FOLDER, STATE],
  ])('Message with subFolder: %p and type: %p can be handled', (subFolder: string, subType: string) => {
    // arrange
    const event = createEventFromTypes(subFolder, subType);
    // act
    udmiEventHandler.handleUdmiEvent(event);
    // assert
    expect(mockDeviceHandle).toHaveBeenCalled();
  });
});
