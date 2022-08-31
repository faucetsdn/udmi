import { DAO } from '../dao/DAO';
import { Handler } from '../Handler';
import { UdmiMessage } from '../model/UdmiMessage';
import { Site, SiteKey, SiteValidation } from './model/Site';
import { getSiteDocument, getSiteKey, getSiteValidationMessage } from './SiteDocumentUtils';

export class SiteHandler implements Handler {
  constructor(private siteDao: DAO<Site>, private siteValidationDao: DAO<SiteValidation>) {}

  async handle(udmiMessage: UdmiMessage): Promise<void> {
    const siteKey: SiteKey = getSiteKey(udmiMessage);

    // we'll upsert the site document in case it exists
    const site: Site = getSiteDocument(udmiMessage);
    this.siteDao.upsert(siteKey, site);

    // we want to insert new validation message and leave the old ones alone
    const siteValidation: SiteValidation = getSiteValidationMessage(udmiMessage);
    this.siteValidationDao.insert(siteValidation);
  }
}
