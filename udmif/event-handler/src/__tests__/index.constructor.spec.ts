import { handleUdmiEvent } from '../index';
import * as factory from '../DeviceDaoFactory';
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

const codedData: string =
  'ewogICJsb2NhdGlvbiIgOiB7CiAgICAic2l0ZSIgOiAiWlotVFJJLUZFQ1RBIiwKICAgICJzZWN0aW9uIiA6ICIyLTNOOEMiLAogICAgInBvc2l0aW9uIiA6IHsKICAgICAgIngiIDogMTExLjAsCiAgICAgICJ5IiA6IDEwMi4zCiAgICB9CiAgfSwKICAicGh5c2ljYWxfdGFnIiA6IHsKICAgICJhc3NldCIgOiB7CiAgICAgICJndWlkIiA6ICJkcnc6Ly9UQkMiLAogICAgICAic2l0ZSIgOiAiWlotVFJJLUZFQ1RBIiwKICAgICAgIm5hbWUiIDogIkFIVS0xIgogICAgfQogIH0KfQ==';

describe('index.constructor', () => {
  let handleUdmiEventMock = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();

    // arrange
    jest.spyOn(factory, 'getDeviceDAO').mockImplementation(jest.fn());
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
