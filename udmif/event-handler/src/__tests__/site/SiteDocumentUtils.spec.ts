import { Site, SiteValidation } from '../../site/model/Site';
import { UdmiEvent } from '../../udmi/UdmiEvent';
import { SITE_VALIDATION_EVENT } from '../dataUtils';
import { getSiteDocument, getSiteValidationDocument } from '../../site/SiteDocumentUtils';

describe('SiteDocumentUtils.getSiteDocument', () => {
  const SITE_ID: string = 'reg-1';
  test('returns a site document', () => {
    // arrange
    const event: UdmiEvent = SITE_VALIDATION_EVENT;
    const expectedSiteDocumet: Site = { name: SITE_ID, validation: event.data };

    // act
    const siteDocument: Site = getSiteDocument(event);

    // assert
    expect(siteDocument).toEqual(expectedSiteDocumet);
  });
});

describe('SiteDocumentUtils.getSiteValidationDocument', () => {
  test('returns a site document', () => {
    // arrange
    const event: UdmiEvent = SITE_VALIDATION_EVENT;
    const expectedSiteDocumet: SiteValidation = {
      timestamp: new Date(event.data.timestamp),
      siteName: event.attributes.deviceRegistryId,
      message: event.data,
    };

    // act
    const document: SiteValidation = getSiteValidationDocument(event);

    // assert
    expect(document).toEqual(expectedSiteDocumet);
  });
});
