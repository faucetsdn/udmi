import { UdmiMessage } from '../model/UdmiMessage';
import UdmiMessageHandler from '../UdmiMessageHandler';
import { EVENT, MODEL, POINTSET_SUB_FOLDER, STATE, SYSTEM_SUB_FOLDER, VALIDATION_SUB_FOLDER } from '../MessageUtils';

const AHU_ID: string = 'AHU-1';
const AHU_REGISTRY_ID: string = 'reg-1';

describe('UdmiMessageHandler', () => {
  const event: UdmiMessage = createMessage(
    {
      deviceId: AHU_ID,
      deviceRegistryId: AHU_REGISTRY_ID,
      subFolder: SYSTEM_SUB_FOLDER,
      subType: MODEL,
    },
    {
      location: {
        site: 'ZZ-TRI-FECTA',
        section: '2-3N8C',
      },
    }
  );

  let udmiMessageHandler: UdmiMessageHandler;
  const upsertMock = jest.fn();
  const getMock = jest.fn();
  const createMock = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    udmiMessageHandler = new UdmiMessageHandler(
      { upsert: upsertMock, get: getMock },
      { createDeviceDocument: createMock }
    );
  });

  test('Calling handleUdmiEvent invokes upsert', async () => {
    await udmiMessageHandler.handleUdmiEvent(event);

    expect(getMock).toHaveBeenCalled();
    expect(upsertMock).toHaveBeenCalled();
  });

  test('Unhandled Message Types are logged', async () => {
    const message: UdmiMessage = createMessage(
      {
        deviceId: AHU_ID,
        deviceRegistryId: AHU_REGISTRY_ID,
        subFolder: 'random',
        subType: MODEL,
      },
      {}
    );

    // arrange
    jest.spyOn(global.console, 'warn');

    // act
    await udmiMessageHandler.handleUdmiEvent(message);

    // assert
    expect(console.warn).toHaveBeenCalledWith('Skipping UDMI message: ' + JSON.stringify(message));
  });

  test.each([[null, AHU_REGISTRY_ID]])(
    'throws an exception if a mandatory field (deviceId, deviceRegistryId) is are (%p, %p)',
    async (deviceId: string, deviceRegistryId: string) => {
      // arrange
      jest.spyOn(global.console, 'error');

      const message = createMessage({
        deviceId,
        deviceRegistryId,
        subFolder: SYSTEM_SUB_FOLDER,
        subType: MODEL,
      });
      // act and assert
      expect(udmiMessageHandler.handleUdmiEvent(message)).rejects.toThrow('An invalid device id was submitted');
    }
  );

  test.each([[AHU_ID, null]])(
    'throws an exception if a mandatory field (deviceId, deviceNumId) is are (%p, %p)',
    async (deviceId: string, deviceRegistryId: string) => {
      // arrange
      jest.spyOn(global.console, 'error');

      const message = createMessage({
        deviceId,
        deviceRegistryId,
        subFolder: SYSTEM_SUB_FOLDER,
        subType: MODEL,
      });
      // act and assert
      expect(udmiMessageHandler.handleUdmiEvent(message)).rejects.toThrow('An invalid site was submitted');
    }
  );

  test.each([
    [SYSTEM_SUB_FOLDER, STATE, AHU_ID, true],
    [SYSTEM_SUB_FOLDER, EVENT, AHU_ID, false],
    [POINTSET_SUB_FOLDER, STATE, AHU_ID, true],
    [POINTSET_SUB_FOLDER, EVENT, AHU_ID, false],
    [VALIDATION_SUB_FOLDER, EVENT, AHU_ID, true],
    [VALIDATION_SUB_FOLDER, EVENT, '_validator', false],
    [VALIDATION_SUB_FOLDER, STATE, AHU_ID, false],
    ['random', STATE, AHU_ID, false],
  ])(
    'Message with subFolder: %p and type: %p can be handled',
    async (subFolder: string, subType: string, deviceId: string, result: boolean) => {
      const message = createMessageFromTypes(subFolder, subType, deviceId);
      console.log(message);
      await udmiMessageHandler.handleUdmiEvent(message);
      if (result) {
        expect(getMock).toHaveBeenCalled();
        expect(createMock).toHaveBeenCalled();
        expect(upsertMock).toHaveBeenCalled();
      }
    }
  );
});

function createMessageFromTypes(subFolder: string, subType: string, deviceId: string = 'AHU-1'): UdmiMessage {
  const defaultAttributes = {
    deviceId,
    deviceRegistryId: AHU_REGISTRY_ID,
    subFolder,
    subType,
  };
  return { attributes: { ...defaultAttributes }, data: {} };
}

function createMessage(attributes: any, data: object = {}): UdmiMessage {
  return { attributes, data };
}
