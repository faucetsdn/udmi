import { UdmiMessage } from '../../model/UdmiMessage';
import { SYSTEM_SUB_FOLDER, VALIDATION_SUB_FOLDER } from '../../MessageUtils';
import { Handler } from '../../Handler';
import { SiteHandler } from '../../site/SiteHandler';
import { createMessage } from '../dataUtils';

const SITE_ID: string = 'reg-1';

describe('SiteHandler', () => {
  const event: UdmiMessage = createMessage(
    {
      deviceId: '_validator',
      deviceRegistryId: SITE_ID,
      subFolder: VALIDATION_SUB_FOLDER,
    },
    {
      version: '1.3.14',
      timestamp: '2018-08-26T21:39:29.364Z',
      last_updated: '2022-07-16T18:27:19Z',
      summary: {
        correct_devices: ['AHU-22'],
        extra_devices: [],
        missing_devices: ['GAT-123', 'SNS-4'],
        error_devices: ['AHU-1'],
      },
      devices: {
        'AHU-1': {
          last_seen: '2022-07-16T18:27:19Z',
          oldest_mark: '2022-07-16T18:27:19Z',
          status: {
            message: 'Tickity Boo',
            category: 'system.config.apply',
            timestamp: '2018-08-26T21:39:30.364Z',
            level: 600,
          },
        },
      },
    }
  );

  let siteHandler: Handler;
  const upsertMock = jest.fn();
  const getMock = jest.fn();
  const getDocumentMock = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    siteHandler = new SiteHandler({ upsert: upsertMock, get: getMock }, { getSiteDocument: getDocumentMock });
  });

  test('Calling handleUdmiEvent invokes upsert', async () => {
    await siteHandler.handle(event);

    expect(upsertMock).toHaveBeenCalled();
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
