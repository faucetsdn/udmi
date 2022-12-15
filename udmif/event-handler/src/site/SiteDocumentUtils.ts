import { ValidationEvent } from '../udmi/UdmiEvent';
import { Site, SiteValidation } from './model/Site';

export function getSiteDocument(udmiEvent: ValidationEvent): Site {
  return { name: udmiEvent.attributes.deviceRegistryId, validation: udmiEvent.data };
}

export function getSiteValidationDocument(udmiEvent: ValidationEvent): SiteValidation {
  return {
    timestamp: new Date(udmiEvent.data.timestamp),
    siteName: udmiEvent.attributes.deviceRegistryId,
    message: udmiEvent.data,
  };
}
