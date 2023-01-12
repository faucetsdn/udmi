import { Handler } from '../../Handler';
import { SiteHandler } from '../../site/SiteHandler';
import { SITE_VALIDATION_EVENT } from '../dataUtils';
import { insertMock, mockDAO, upsertMock } from '../MockDAO';

describe('SiteHandler', () => {
  let siteHandler: Handler;

  beforeEach(() => {
    jest.clearAllMocks();
    siteHandler = new SiteHandler(mockDAO, mockDAO);
  });

  test('Calling handleUdmiEvent invokes upsert', async () => {
    // arrange and act
    await siteHandler.handle(SITE_VALIDATION_EVENT);

    // assert
    expect(upsertMock).toHaveBeenCalled();
    expect(insertMock).toHaveBeenCalled();
  });
});
