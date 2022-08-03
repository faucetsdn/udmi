import { UdmiMessage } from '../UdmiMessage';
import UdmiMessageHandler, { getDeviceKey } from '../UdmiMessageHandler';

describe('UdmiMessageHandler', () => {
  const event: UdmiMessage = {
    attributes: {
      deviceId: 'AHU-1',
      deviceNumId: '2625324262579600',
      subFolder: 'system',
      subType: 'model',
    },
    data: {
      location: {
        site: 'ZZ-TRI-FECTA',
        section: '2-3N8C',
      },
    },
  };

  let udmiMessageHandler: UdmiMessageHandler;
  const upsertMock = jest.fn();
  const getMock = jest.fn();

  beforeEach(() => {
    udmiMessageHandler = new UdmiMessageHandler({ upsert: upsertMock, get: getMock });
  });

  test('Calling handleUdmiEvent invokes upsert', async () => {
    await udmiMessageHandler.handleUdmiEvent(event);

    expect(getMock).toHaveBeenCalled();
    expect(upsertMock).toHaveBeenCalled();
  });

  test('Exception is logged', async () => {
    // arrange
    jest.spyOn(global.console, 'error');
    upsertMock.mockImplementation(() => {
      throw Error('some error');
    });

    // act
    await udmiMessageHandler.handleUdmiEvent(event);

    // assert
    expect(console.error).toHaveBeenCalled();
  });
});

describe('getDocumentKey', () => {
  const deviceId: string = 'AHU-1';
  const deviceNumId: string = '2625324262579600';

  test('returns a key', () => {
    const event = {
      attributes: {
        deviceId,
        deviceNumId,
      },
      data: {},
    };

    expect(getDeviceKey(event)).toEqual({ name: deviceId, id: deviceNumId });
  });

  test('throws an exception if the deviceId is missing', () => {
    const event = {
      attributes: {
        deviceId: null,
        deviceNumId,
      },
      data: {},
    };
    expect(() => {
      getDeviceKey(event);
    }).toThrowError('An invalid device name or id was submitted');
  });

  test('throws an exception if the deviceNumId is missing', () => {
    const event = {
      attributes: {
        deviceId,
        deviceNumId: null,
      },
      data: {},
    };
    expect(() => {
      getDeviceKey(event);
    }).toThrowError('An invalid device name or id was submitted');
  });
});
