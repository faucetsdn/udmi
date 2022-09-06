import { DAO } from '../dao/DAO';
import { Handler } from '../Handler';
import { UdmiEvent } from '../model/UdmiEvent';
import { Site, SiteKey, SiteValidation } from './model/Site';
import { getSiteDocument, getSiteKey, getSiteValidationDocument } from './SiteDocumentUtils';

export class SiteHandler implements Handler {
  constructor(private siteDao: DAO<Site>, private siteValidationDao: DAO<SiteValidation>) {}

  async handle(udmiEvent: UdmiEvent): Promise<void> {
    const siteKey: SiteKey = getSiteKey(udmiEvent);

    // we'll upsert the site document in case it exists
    const site: Site = getSiteDocument(udmiEvent);
    this.siteDao.upsert(siteKey, site);

    // we want to insert new validation message and leave the old ones alone
    const siteValidation: SiteValidation = getSiteValidationDocument(udmiEvent);
    this.siteValidationDao.insert(siteValidation);
  }
}
