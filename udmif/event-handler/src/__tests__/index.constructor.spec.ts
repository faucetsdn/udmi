import { handleUdmiEvent } from '../index';
import * as MongoDao from '../dao/mongo/MongoDAO';
import UdmiMessageHandler from '../UdmiMessageHandler';
import { event } from './dataUtils';

jest.mock('../UdmiMessageHandler');

describe('index.constructor', () => {
  let handleUdmiEventMock = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();

    // arrange
    jest.spyOn(MongoDao, 'getDeviceDAO').mockImplementation(jest.fn());
    jest.spyOn(MongoDao, 'getSiteDAO').mockImplementation(jest.fn());
    jest.spyOn(MongoDao, 'getSiteValidationDAO').mockImplementation(jest.fn());
    jest.spyOn(MongoDao, 'getDeviceValidationDAO').mockImplementation(jest.fn());
    jest.spyOn(UdmiMessageHandler.prototype, 'handleUdmiEvent').mockImplementation(handleUdmiEventMock);
  });

  test('UdmiMessageHandler is not created if it has already been created', async () => {
    // act
    await handleUdmiEvent(event, {});
    await handleUdmiEvent(event, {});

    // assert
    expect(UdmiMessageHandler).toHaveBeenCalledTimes(1);
    expect(handleUdmiEventMock).toHaveBeenCalledTimes(2);
  });
});
