import { handleUdmiEvent } from '../index';
import * as MongoDAO from '../dao/mongo/MongoDAO';
import UdmiEventHandler from '../UdmiEventHandler';
import { InvalidEventError } from '../InvalidEventError';
import { SYSTEM_MODEL_EVENT } from './dataUtils';

jest.mock('../UdmiEventHandler');

describe('index', () => {
  let deviceDAOSpy;
  let deviceValidationDAOSpy;
  let siteDAOSpy;
  let siteValidationDAOSpy;
  let handleUdmiEventSpy;
  const mockHandleEvent = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();

    // arrange
    deviceDAOSpy = jest.spyOn(MongoDAO, 'getDeviceDAO').mockImplementation(jest.fn());
    siteDAOSpy = jest.spyOn(MongoDAO, 'getSiteDAO').mockImplementation(jest.fn());
    siteValidationDAOSpy = jest.spyOn(MongoDAO, 'getSiteValidationDAO').mockImplementation(jest.fn());
    deviceValidationDAOSpy = jest.spyOn(MongoDAO, 'getDeviceValidationDAO').mockImplementation(jest.fn());
    handleUdmiEventSpy = jest.spyOn(UdmiEventHandler.prototype, 'handleUdmiEvent').mockImplementation(mockHandleEvent);
  });

  test('handleUdmiEvent is called when an event is passed in', async () => {
    // act
    await handleUdmiEvent(SYSTEM_MODEL_EVENT, {});

    // assert
    expect(handleUdmiEventSpy).toHaveBeenCalled();
    expect(deviceDAOSpy).toHaveBeenCalled();
    expect(siteDAOSpy).toHaveBeenCalled();
    expect(siteValidationDAOSpy).toHaveBeenCalled();
    expect(deviceValidationDAOSpy).toHaveBeenCalled();
  });

  test('Exception is logged', async () => {
    // arrange
    jest.spyOn(global.console, 'error');
    mockHandleEvent.mockImplementation(() => {
      throw Error('some error');
    });

    // act
    await handleUdmiEvent(SYSTEM_MODEL_EVENT, {});

    // assert
    expect(console.error).toHaveBeenCalled();
  });

  test('InvalidEventError is logged', async () => {
    // arrange
    jest.spyOn(global.console, 'error');
    mockHandleEvent.mockImplementation(() => {
      throw new InvalidEventError('some error');
    });

    // act
    await handleUdmiEvent(SYSTEM_MODEL_EVENT, {});

    // assert
    expect(console.error).toHaveBeenCalled();
  });
});
