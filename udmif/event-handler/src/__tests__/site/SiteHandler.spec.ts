import { UdmiMessage } from '../../model/UdmiMessage';
import { SYSTEM_SUB_FOLDER } from '../../MessageUtils';
import { Handler } from '../../Handler';
import { SiteHandler } from '../../site/SiteHandler';
import { createMessage, SITE_VALIDATION_EVENT } from '../dataUtils';
import { insertMock, mockDAO, upsertMock } from '../MockDAO';

describe('SiteHandler', () => {
  const event: UdmiMessage = SITE_VALIDATION_EVENT;

  let siteHandler: Handler;

  beforeEach(() => {
    jest.clearAllMocks();
    siteHandler = new SiteHandler(mockDAO, mockDAO);
  });

  test('Calling handleUdmiEvent invokes upsert', async () => {
    await siteHandler.handle(event);

    expect(upsertMock).toHaveBeenCalled();
    expect(insertMock).toHaveBeenCalled();
  });

  test('throws an exception if a mandatory field deviceRegistryId is null', async () => {
    // arrange
    jest.spyOn(global.console, 'error');

    const message = createMessage({
      deviceId: '_validator',
      deviceRegistryId: null,
      subFolder: SYSTEM_SUB_FOLDER,
    });
    // act and assert
    expect(siteHandler.handle(message)).rejects.toThrow('An invalid site name was submitted');
  });
});
