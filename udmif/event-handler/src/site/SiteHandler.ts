import { DAO } from '../dao/DAO';
import { Handler } from '../Handler';
import { UdmiEvent } from '../model/UdmiEvent';
import { Site, SiteKey, SiteValidation } from './model/Site';
import { getSiteDocument, getSiteKey, getSiteValidationDocument } from './SiteDocumentUtils';

export class SiteHandler implements Handler {
  constructor(
    private siteMongoDao: DAO<Site>,
    private sitePGDao: DAO<Site>,
    private siteMongoValidationDao: DAO<SiteValidation>,
    private sitePGValidationDao: DAO<SiteValidation>
  ) {}

  async handle(udmiEvent: UdmiEvent): Promise<void> {
    const siteKey: SiteKey = getSiteKey(udmiEvent);
    const site: Site = getSiteDocument(udmiEvent);
    const siteValidation: SiteValidation = getSiteValidationDocument(udmiEvent);

    // we'll upsert the site document in mongo in case it exists, this will get removed once postgresql is working
    this.siteMongoDao.upsert(siteKey, site);
    if (this.sitePGDao) this.sitePGDao.upsert(siteKey, site);

    // we want to insert new validation message and leave the old ones alone
    this.siteMongoValidationDao.insert(siteValidation);
    if (this.sitePGValidationDao) this.sitePGValidationDao.insert(siteValidation);
  }
}
