import { handleUdmiEvent } from '../index';
import * as MongoDAO from '../dao/mongo/MongoDAO';
import UdmiMessageHandler from '../UdmiMessageHandler';
import { InvalidMessageError } from '../InvalidMessageError';
import { event } from './dataUtils';

jest.mock('../UdmiMessageHandler');

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
    handleUdmiEventSpy = jest
      .spyOn(UdmiMessageHandler.prototype, 'handleUdmiEvent')
      .mockImplementation(mockHandleEvent);
  });

  test('handleUdmiEvent is called when an event is passed in', async () => {
    // act
    await handleUdmiEvent(event, {});

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
    await handleUdmiEvent(event, {});

    // assert
    expect(console.error).toHaveBeenCalled();
  });

  test('InvalidMessageError is logged', async () => {
    // arrange
    jest.spyOn(global.console, 'error');
    mockHandleEvent.mockImplementation(() => {
      throw new InvalidMessageError('some error');
    });

    // act
    await handleUdmiEvent(event, {});

    // assert
    expect(console.error).toHaveBeenCalled();
  });
});
