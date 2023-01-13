import { DAO } from '../dao/DAO';
import { Handler } from '../Handler';
import { UdmiEvent } from '../udmi/UdmiEvent';
import { PRIMARY_KEYS, Site, SiteValidation } from './model/Site';
import { getSiteDocument, getSiteValidationDocument } from './SiteDocumentUtils';

export class SiteHandler implements Handler {
  constructor(private sitePGDao: DAO<Site>, private sitePGValidationDao: DAO<SiteValidation>) {}

  async handle(udmiEvent: UdmiEvent): Promise<void> {
    const originalSite: Site = await this.sitePGDao.get({ name: udmiEvent.attributes.deviceRegistryId });
    const site: Site = getSiteDocument(originalSite, udmiEvent);
    await this.sitePGDao.upsert(site, PRIMARY_KEYS);

    const siteValidation: SiteValidation = getSiteValidationDocument(udmiEvent);
    await this.sitePGValidationDao.insert(siteValidation);
  }
}
