import { UdmiMessage } from '../model/UdmiMessage';
import { Site } from './model/Site';

export class SiteDocumentFactory {
  getSiteDocument(udmiMessage: UdmiMessage): Site {
    return { name: udmiMessage.attributes.deviceRegistryId };
  }
}
