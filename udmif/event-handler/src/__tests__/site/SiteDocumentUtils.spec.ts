import { Site, SiteValidation } from '../../site/model/Site';
import { UdmiEvent } from '../../model/UdmiEvent';
import { SITE_VALIDATION_EVENT } from '../dataUtils';
import { getSiteDocument, getSiteKey, getSiteValidationDocument } from '../../site/SiteDocumentUtils';

describe('SiteDocumentUtils.getSiteDocument', () => {
  const SITE_ID: string = 'reg-1';
  test('returns a site document', () => {
    // arrange
    const event: UdmiEvent = SITE_VALIDATION_EVENT;
    const expectedSiteDocumet: Site = { name: SITE_ID, lastMessage: event.data };

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
      data: event.data,
    };

    // act
    const document: SiteValidation = getSiteValidationDocument(event);

    // assert
    expect(document).toEqual(expectedSiteDocumet);
  });
});

describe('SiteDocumentUtils.getSiteKey', () => {
  test('throws an exception if a mandatory field deviceRegistryId is null', () => {
    // arrange
    const message: UdmiEvent = SITE_VALIDATION_EVENT;
    message.attributes.deviceRegistryId = null;

    // act and assert
    expect(() => {
      getSiteKey(message);
    }).toThrow('An invalid site name was submitted');
  });
});
