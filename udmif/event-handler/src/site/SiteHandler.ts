import { DAO } from '../dao/DAO';
import { Handler } from '../Handler';
import { UdmiEvent } from '../udmi/UdmiEvent';
import { PRIMARY_KEYS, Site, SiteValidation } from './model/Site';
import { getSiteDocument, getSiteValidationDocument } from './SiteDocumentUtils';

export class SiteHandler implements Handler {
  constructor(
    private sitePGDao: DAO<Site>,
    private sitePGValidationDao: DAO<SiteValidation>
  ) { }

  async handle(udmiEvent: UdmiEvent): Promise<void> {
    // we'll upsert the site document in mongo in case it exists, this will get removed once postgresql is working
    const site: Site = getSiteDocument(udmiEvent);
    if (this.sitePGDao) this.sitePGDao.upsert(site, PRIMARY_KEYS);

    const siteValidation: SiteValidation = getSiteValidationDocument(udmiEvent);
    // we want to insert new validation message and leave the old ones alone
    if (this.sitePGValidationDao) this.sitePGValidationDao.insert(siteValidation);
  }
}
