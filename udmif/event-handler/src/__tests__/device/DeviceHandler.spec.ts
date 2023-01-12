import { Handler } from '../../Handler';
import { DeviceHandler } from '../../device/DeviceHandler';
import { DEVICE_VALIDATION_EVENT, POINTSET_STATE_EVENT, SYSTEM_MODEL_EVENT } from '../dataUtils';
import { getMock, insertMock, mockDAO, upsertMock } from '../MockDAO';

describe('DeviceHandler', () => {
  let deviceHandler: Handler;

  beforeEach(() => {
    jest.clearAllMocks();
    deviceHandler = new DeviceHandler(mockDAO, mockDAO);
  });

  test.each([SYSTEM_MODEL_EVENT, POINTSET_STATE_EVENT, DEVICE_VALIDATION_EVENT])(
    'Calling handleUdmiEvent invokes upsert for any event',
    async (event) => {
      // arrange and act
      await deviceHandler.handle(event);

      // assert
      expect(upsertMock).toHaveBeenCalled();
    }
  );

  test.each([true, false])('When an existing document is %p, the points get defaulted either way', async (exists) => {
    // arrange
    getMock.mockReturnValue(exists ? {} : undefined);

    // act
    await deviceHandler.handle(SYSTEM_MODEL_EVENT);

    // assert
    expect(upsertMock).toHaveBeenCalled();
  });

  test('Validation Message is recorded as an insert', async () => {
    // arrange and act
    await deviceHandler.handle(DEVICE_VALIDATION_EVENT);

    // assert
    expect(insertMock).toHaveBeenCalled();
  });
});
