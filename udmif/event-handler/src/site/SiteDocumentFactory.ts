import { ValidationMessage } from '../model/UdmiMessage';
import { Site } from './model/Site';

export class SiteDocumentFactory {
  getSiteDocument(udmiMessage: ValidationMessage): Site {
    return { name: udmiMessage.attributes.deviceRegistryId, errorDevices: udmiMessage.data.summary.error_devices };
  }
}
