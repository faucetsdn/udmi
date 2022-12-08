import { UdmiEvent } from '../../model/UdmiEvent';
import { Handler } from '../../Handler';
import { SiteHandler } from '../../site/SiteHandler';
import { SITE_VALIDATION_EVENT } from '../dataUtils';
import { insertMock, mockDAO, upsertMock } from '../MockDAO';

describe('SiteHandler', () => {
  const event: UdmiEvent = SITE_VALIDATION_EVENT;

  let siteHandler: Handler;

  beforeEach(() => {
    jest.clearAllMocks();
    siteHandler = new SiteHandler(mockDAO, mockDAO, mockDAO, mockDAO);
  });

  test('Calling handleUdmiEvent invokes upsert', async () => {
    // arrange and act
    await siteHandler.handle(event);

    // assert
    expect(upsertMock).toHaveBeenCalled();
    expect(insertMock).toHaveBeenCalled();
  });

  test("Site is not saved to PostgreSQL (PG) if the PG Dao's are null", async () => {
    siteHandler = new SiteHandler(mockDAO, null, mockDAO, null);

    // arrange and act
    await siteHandler.handle(event);

    // assert
    expect(insertMock).toHaveBeenCalledTimes(1);
  });
});
