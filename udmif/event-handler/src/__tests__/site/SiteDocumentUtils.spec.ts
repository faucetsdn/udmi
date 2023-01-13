import { Site, SiteValidation } from '../../site/model/Site';
import { UdmiEvent } from '../../udmi/UdmiEvent';
import { SITE_VALIDATION_EVENT } from '../dataUtils';
import { getSiteDocument, getSiteValidationDocument, mergeValidations } from '../../site/SiteDocumentUtils';
import { Validation } from '../../model/Validation';

describe('SiteDocumentUtils.getSiteDocument', () => {
  const SITE_ID: string = 'reg-1';

  test('returns a site document', () => {
    // arrange
    const event: UdmiEvent = SITE_VALIDATION_EVENT;
    const expectedSiteDocumet: Site = { name: SITE_ID, validation: event.data };

    // act
    const siteDocument: Site = getSiteDocument(null, event);

    // assert
    expect(siteDocument).toEqual(expectedSiteDocumet);
  });

  test('returns a merged validation with some fields left alone', () => {
    // arrange
    const name: string = SITE_ID;
    const status = null;
    const pointset = [];
    const errors = [];
    const devices = null;
    const summary = null;

    const existingSite: Site = {
      name,
      validation: {
        version: '1.3.14',
        timestamp: '2018-08-26T21:39:29.364Z',
        last_updated: '2018-08-26T21:39:29.364Z',
        errors,
        pointset,
        devices,
        summary,
        status,
      },
    };

    // act
    const site: Site = getSiteDocument(existingSite, SITE_VALIDATION_EVENT);

    // assert
    expect(site).toEqual({ name, validation: { ...SITE_VALIDATION_EVENT.data, status, pointset, errors } });
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
