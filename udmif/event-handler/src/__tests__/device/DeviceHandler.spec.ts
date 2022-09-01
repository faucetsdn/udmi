import { UdmiMessage } from '../../model/UdmiMessage';
import { EVENT, MODEL, POINTSET_SUB_FOLDER, STATE, SYSTEM_SUB_FOLDER, VALIDATION_SUB_FOLDER } from '../../MessageUtils';
import { Handler } from '../../Handler';
import { DeviceHandler } from '../../device/DeviceHandler';
import { createMessage, createMessageFromTypes } from '../dataUtils';
import { getMock, insertMock, mockDAO, upsertMock } from '../MockDAO';

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
    deviceHandler = new DeviceHandler(mockDAO, mockDAO);
  });

  test('Calling handleUdmiEvent invokes upsert', async () => {
    // arrange
    getMock.mockReturnValue({});

    // act
    await deviceHandler.handle(event);

    // assert
    expect(getMock).toHaveBeenCalled();
    expect(upsertMock).toHaveBeenCalled();
  });

  test('Points are defaulted to []', async () => {
    // arrange
    const message = createMessageFromTypes(POINTSET_SUB_FOLDER, STATE, AHU_ID);
    getMock.mockResolvedValue({});
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

  test('Validation Message is recorded', async () => {
    // arrange
    const message = createMessage(
      {
        deviceId: AHU_ID,
        deviceRegistryId: AHU_REGISTRY_ID,
        subFolder: VALIDATION_SUB_FOLDER,
      },
      {
        timestamp: '2022-08-03T17:28:49Z',
        version: '1.3.14',
        status: {
          timestamp: '2022-08-03T17:28:49Z',
          message: 'Multiple validation errors',
          detail:
            'While converting to json node: 2 schema violations found; While converting to json node: 1 schema violations found',
          category: 'category-x',
          level: 600,
        },
        errors: [
          {
            message: 'While converting to json node: 2 schema violations found',
            level: 500,
            category: 'category-x',
          },
          {
            message: 'While converting to json node: 1 schema violations found',
            level: 500,
            category: 'category-x',
          },
        ],
      }
    );

    // act
    await deviceHandler.handle(message);

    // assert
    expect(insertMock).toHaveBeenCalled();
  });
});
