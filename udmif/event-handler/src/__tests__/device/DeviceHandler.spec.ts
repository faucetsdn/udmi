import { UdmiMessage } from '../../model/UdmiMessage';
import { MODEL, POINTSET_SUB_FOLDER, STATE, SYSTEM_SUB_FOLDER } from '../../MessageUtils';
import { Handler } from '../../Handler';
import { DeviceHandler } from '../../device/DeviceHandler';
import { createMessage, createMessageFromTypes } from '../dataUtils';
import { getMock, mockDAO, upsertMock } from '../MockDAO';

const AHU_ID: string = 'AHU-1';
const AHU_REGISTRY_ID: string = 'reg-1';

describe('DeviceHandler', () => {
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

  let deviceHandler: Handler;

  beforeEach(() => {
    jest.clearAllMocks();
    deviceHandler = new DeviceHandler(mockDAO);
  });

  test('Calling handleUdmiEvent invokes upsert', async () => {
    await deviceHandler.handle(event);

    expect(getMock).toHaveBeenCalled();
    expect(upsertMock).toHaveBeenCalled();
  });

  test('throws an exception if a mandatory field (deviceId, deviceRegistryId) is are (%p, %p)', async () => {
    // arrange
    jest.spyOn(global.console, 'error');

    const message = createMessage({
      deviceId: null,
      deviceRegistryId: AHU_REGISTRY_ID,
      subFolder: SYSTEM_SUB_FOLDER,
      subType: MODEL,
    });
    // act and assert
    expect(deviceHandler.handle(message)).rejects.toThrow('An invalid device id was submitted');
  });

  test('throws an exception if a mandatory field (deviceId, deviceNumId) is are (%p, %p)', async () => {
    // arrange
    jest.spyOn(global.console, 'error');

    const message = createMessage({
      deviceId: AHU_ID,
      deviceRegistryId: null,
      subFolder: SYSTEM_SUB_FOLDER,
      subType: MODEL,
    });
    // act and assert
    expect(deviceHandler.handle(message)).rejects.toThrow('An invalid site was submitted');
  });

  test('Points are defaulted to []', async () => {
    // arrange
    const message = createMessageFromTypes(POINTSET_SUB_FOLDER, STATE, AHU_ID);
    getMock.mockResolvedValue(null);
    // act
    await deviceHandler.handle(message);
    // assert
    expect(getMock).toHaveBeenCalled();
    expect(upsertMock).toHaveBeenCalled();
  });

  test('Points are array if array found', async () => {
    const message = createMessageFromTypes(POINTSET_SUB_FOLDER, STATE, AHU_ID);
    getMock.mockResolvedValue([]);
    await deviceHandler.handle(message);
    expect(getMock).toHaveBeenCalled();
    expect(upsertMock).toHaveBeenCalled();
  });
});
