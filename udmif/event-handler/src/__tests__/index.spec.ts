import { handleUdmiEvent } from '../index';
import * as DAO from '../dao/DAO';
import UdmiMessageHandler from '../UdmiMessageHandler';

jest.mock('../UdmiMessageHandler');

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

describe('index', () => {
  let deviceFactorySpy;
  let siteFactorySpy;
  let handleUdmiEventSpy;
  const mockHandleEvent = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();

    // arrange
    deviceFactorySpy = jest.spyOn(DAO, 'getDeviceDAO').mockImplementation(jest.fn());
    siteFactorySpy = jest.spyOn(DAO, 'getSiteDAO').mockImplementation(jest.fn());
    handleUdmiEventSpy = jest
      .spyOn(UdmiMessageHandler.prototype, 'handleUdmiEvent')
      .mockImplementation(mockHandleEvent);
  });

  test('handleUdmiEvent is called when an event is passed in', async () => {
    // act
    await handleUdmiEvent(event, {});

    // assert
    expect(handleUdmiEventSpy).toHaveBeenCalled();
    expect(deviceFactorySpy).toHaveBeenCalled();
    expect(siteFactorySpy).toHaveBeenCalled();
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
});
