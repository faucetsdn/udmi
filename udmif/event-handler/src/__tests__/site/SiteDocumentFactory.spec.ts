import { Site } from '../../site/model/Site';
import { VALIDATION_SUB_FOLDER } from '../../MessageUtils';
import { UdmiMessage } from '../../model/UdmiMessage';
import { SiteDocumentFactory } from '../../site/SiteDocumentFactory';
import { createMessage } from '../dataUtils';

describe('SiteDocumentFactory.getSiteDocument', () => {
  const SITE_ID: string = 'reg-1';
  test('returns a site document', () => {
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

    const siteDocument: Site = new SiteDocumentFactory().getSiteDocument(event);
    const expectedSiteDocumet: Site = { name: SITE_ID, errorDevices: ['AHU-1'] };
    expect(siteDocument).toEqual(expectedSiteDocumet);
  });
});
