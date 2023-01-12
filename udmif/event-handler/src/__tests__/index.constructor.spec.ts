import { handleUdmiEvent } from '../index';
import { SYSTEM_MODEL_EVENT } from './dataUtils';
import UdmiEventHandler from '../udmi/UdmiEventHandler';

jest.mock('../udmi/UdmiEventHandler');

describe('index.constructor', () => {
  let handleUdmiEventMock = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();

    // arrange
    jest.spyOn(UdmiEventHandler.prototype, 'handleUdmiEvent').mockImplementation(handleUdmiEventMock);
  });

  test('UdmiEventHandler is not created if it has already been created', async () => {
    // act
    await handleUdmiEvent(SYSTEM_MODEL_EVENT, {});
    await handleUdmiEvent(SYSTEM_MODEL_EVENT, {});

    // assert
    expect(UdmiEventHandler).toHaveBeenCalledTimes(1);
    expect(handleUdmiEventMock).toHaveBeenCalledTimes(2);
  });
});
