import { UdmiMessage } from '../model/UdmiMessage';
import UdmiMessageHandler, { VALIDATOR_ID } from '../UdmiMessageHandler';
import {
  CONFIG,
  EVENT,
  MODEL,
  POINTSET_SUB_FOLDER,
  STATE,
  SYSTEM_SUB_FOLDER,
  VALIDATION_SUB_FOLDER,
} from '../MessageUtils';
import { Handler } from '../Handler';
import { createMessage, createMessageFromTypes } from './dataUtils';

const AHU_ID: string = 'AHU-1';
const SITE_ID: string = 'site-1';

describe('UdmiMessageHandler', () => {
  const mockDeviceHandle = jest.fn();
  const mockSiteHandle = jest.fn();

  let udmiMessageHandler: UdmiMessageHandler;

  beforeEach(() => {
    jest.clearAllMocks();
    const mockDeviceHandler: Handler = { handle: mockDeviceHandle };
    const mockSiteHandler: Handler = { handle: mockSiteHandle };
    udmiMessageHandler = new UdmiMessageHandler(mockDeviceHandler, mockSiteHandler);
  });

  test('Unhandled Message Types are logged', () => {
    const message: UdmiMessage = createMessage(
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
    udmiMessageHandler.handleUdmiEvent(message);

    // assert
    expect(console.warn).toHaveBeenCalledWith('Skipping UDMI message: ' + JSON.stringify(message));
  });

  test('Handle site messages', () => {
    // arrange
    const message: UdmiMessage = createMessage(
      {
        deviceId: VALIDATOR_ID,
        deviceRegistryId: SITE_ID,
        subFolder: VALIDATION_SUB_FOLDER,
      },
      {}
    );

    // act
    udmiMessageHandler.handleUdmiEvent(message);

    // assert
    expect(mockSiteHandle).toHaveBeenCalled();
  });

  //    [VALIDATION_SUB_FOLDER, EVENT, '_validator'],
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
    const message = createMessageFromTypes(subFolder, subType);
    // act
    udmiMessageHandler.handleUdmiEvent(message);
    // assert
    expect(mockDeviceHandle).toHaveBeenCalled();
  });
});
