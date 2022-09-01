import { Site } from '../../site/model/Site';
import { UdmiMessage } from '../../model/UdmiMessage';
import { SITE_VALIDATION_EVENT } from '../dataUtils';
import { getSiteDocument } from '../../site/SiteDocumentUtils';

describe('SiteDocumentUtils.getSiteDocument', () => {
  const SITE_ID: string = 'reg-1';
  test('returns a site document', () => {
    // arrange
    const event: UdmiMessage = SITE_VALIDATION_EVENT;
    const expectedSiteDocumet: Site = { name: SITE_ID, lastMessage: event.data };

    // act
    const siteDocument: Site = getSiteDocument(event);

    // assert
    expect(siteDocument).toEqual(expectedSiteDocumet);
  });
});
