import UdmiMessageHandler, { getDeviceKey } from '../UdmiMessageHandler';

describe('UdmiMessageHandler', () => {
  const event = {
    attributes: {
      deviceId: 'AHU-1',
      deviceNumId: '2625324262579600',
      deviceRegistryId: 'ZZ-TRI-FECTA',
      projectId: 'labs-333619',
      subFolder: 'system',
      subType: 'model',
    },
    data: 'ewogICJsb2NhdGlvbiIgOiB7CiAgICAic2l0ZSIgOiAiWlotVFJJLUZFQ1RBIiwKICAgICJzZWN0aW9uIiA6ICIyLTNOOEMiLAogICAgInBvc2l0aW9uIiA6IHsKICAgICAgIngiIDogMTExLjAsCiAgICAgICJ5IiA6IDEwMi4zCiAgICB9CiAgfSwKICAicGh5c2ljYWxfdGFnIiA6IHsKICAgICJhc3NldCIgOiB7CiAgICAgICJndWlkIiA6ICJkcnc6Ly9UQkMiLAogICAgICAic2l0ZSIgOiAiWlotVFJJLUZFQ1RBIiwKICAgICAgIm5hbWUiIDogIkFIVS0xIgogICAgfQogIH0KfQ==',
    messageId: '4498812851299125',
    publishTime: '2022-04-25T17:05:33.162Z',
  };

  let udmiMessageHandler: UdmiMessageHandler;
  const upsertMock = jest.fn();

  beforeEach(() => {
    udmiMessageHandler = new UdmiMessageHandler({ upsert: upsertMock });
  });

  test('Calling handleUdmiEvent invokes upsert', async () => {
    await udmiMessageHandler.handleUdmiEvent(event);

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
