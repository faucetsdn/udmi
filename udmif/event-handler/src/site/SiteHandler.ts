import { InvalidMessageError } from '../InvalidMessageError';
import { DAO } from '../dao/DAO';
import { Handler } from '../Handler';
import { UdmiMessage } from '../model/UdmiMessage';
import { Site, SiteKey } from './model/Site';
import { SiteDocumentFactory } from './SiteDocumentFactory';

export class SiteHandler implements Handler {
  constructor(private siteDao: DAO<Site>, private documentFactory: SiteDocumentFactory) {}

  async handle(udmiMessage: UdmiMessage): Promise<void> {
    const siteKey: SiteKey = this.getSiteKey(udmiMessage);
    const site: Site = this.documentFactory.getSiteDocument(udmiMessage);
    this.siteDao.upsert(siteKey, site);
  }

  getSiteKey(udmiMessage: UdmiMessage): SiteKey {
    if (!udmiMessage.attributes.deviceRegistryId) {
      throw new InvalidMessageError('An invalid site name was submitted');
    }
    return { name: udmiMessage.attributes.deviceRegistryId };
  }
}
