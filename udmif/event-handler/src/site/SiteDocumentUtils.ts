import { InvalidEventError } from '../InvalidEventError';
import { UdmiEvent, ValidationEvent } from '../udmi/UdmiEvent';
import { Site, SiteKey, SiteValidation } from './model/Site';

export function getSiteDocument(udmiEvent: ValidationEvent): Site {
  return { name: udmiEvent.attributes.deviceRegistryId, validation: udmiEvent.data };
}

export function getSiteKey(udmiEvent: UdmiEvent): SiteKey {
  if (!udmiEvent.attributes.deviceRegistryId) {
    throw new InvalidEventError('An invalid site name was submitted');
  }
  return { name: udmiEvent.attributes.deviceRegistryId };
}

export function getSiteValidationDocument(udmiEvent: ValidationEvent): SiteValidation {
  return {
    timestamp: new Date(udmiEvent.data.timestamp),
    siteName: udmiEvent.attributes.deviceRegistryId,
    data: udmiEvent.data,
  };
}
