import { UdmiMessage } from '../UdmiMessage';
import UdmiMessageHandler from '../UdmiMessageHandler';
import { EVENT, MODEL, POINTSET_SUB_FOLDER, STATE, SYSTEM_SUB_FOLDER, VALIDATION_SUB_FOLDER } from '../MessageUtils';

const deviceId: string = 'AHU-1';
const deviceNumId: string = '2625324262579600';

describe('UdmiMessageHandler', () => {
  const event: UdmiMessage = createMessage(
    {
      deviceId,
      deviceNumId,
      subFolder: SYSTEM_SUB_FOLDER,
      subType: MODEL,
    },
    {
      location: {
        site: 'ZZ-TRI-FECTA',
        section: '2-3N8C',
      },
    },
  );

  let udmiMessageHandler: UdmiMessageHandler;
  const upsertMock = jest.fn();
  const getMock = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
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

  test('Unhandled Message Types are logged', async () => {

    const message: UdmiMessage = createMessage(
      {
        deviceId,
        deviceNumId,
        subFolder: 'random',
        subType: MODEL,
      },
      {},
    );

    // arrange
    jest.spyOn(global.console, 'warn');

    // act
    await udmiMessageHandler.handleUdmiEvent(message);

    // assert
    expect(console.warn).toHaveBeenCalledWith('Could not handle the following message: ' + JSON.stringify(message));
  });

  test.each([
    [null, deviceNumId],
    [deviceId, null]
  ])('throws an exception if a mandatory field (deviceId, deviceNumId) is are (%p, %p)', async (deviceId: string, deviceNumId: string) => {

    // arrange
    jest.spyOn(global.console, 'error');

    // act
    await udmiMessageHandler.handleUdmiEvent(createMessage({
      deviceId, deviceNumId, subFolder: SYSTEM_SUB_FOLDER, subType: MODEL,
    }));

    // assert
    expect(console.error).toHaveBeenCalledWith('An unexpected error occurred: ', new Error('An invalid device name or id was submitted'));
  });

  test.each([
    [SYSTEM_SUB_FOLDER, STATE, true],
    [SYSTEM_SUB_FOLDER, EVENT, false],
    [POINTSET_SUB_FOLDER, STATE, true],
    [POINTSET_SUB_FOLDER, EVENT, false],
    [VALIDATION_SUB_FOLDER, EVENT, false],
    [VALIDATION_SUB_FOLDER, STATE, false],
    ['random', STATE, false]
  ])('Message with subFolder: %p and type: %p can be handled', async (subFolder: string, subType: string, result: boolean) => {
    const message = createMessageFromTypes(subFolder, subType);
    await udmiMessageHandler.handleUdmiEvent(message);
    if (result) {
      expect(getMock).toHaveBeenCalled();
      expect(upsertMock).toHaveBeenCalled();
    }
  })
});

function createMessageFromTypes(subFolder: string, subType: string): UdmiMessage {
  const defaultAttributes = {
    deviceId,
    deviceNumId,
    subFolder,
    subType
  };
  return { attributes: { ...defaultAttributes }, data: {} };
}

function createMessage(attributes: any, data: object = {}): UdmiMessage {
  return { attributes, data };
}


