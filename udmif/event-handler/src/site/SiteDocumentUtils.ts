import { InvalidMessageError } from '../InvalidMessageError';
import { UdmiMessage, ValidationMessage } from '../model/UdmiMessage';
import { Site, SiteKey, SiteValidation } from './model/Site';

export function getSiteDocument(udmiMessage: ValidationMessage): Site {
  return { name: udmiMessage.attributes.deviceRegistryId, lastMessage: udmiMessage.data };
}

export function getSiteKey(udmiMessage: UdmiMessage): SiteKey {
  if (!udmiMessage.attributes.deviceRegistryId) {
    throw new InvalidMessageError('An invalid site name was submitted');
  }
  return { name: udmiMessage.attributes.deviceRegistryId };
}

export function getSiteValidationMessage(udmiMessage: ValidationMessage): SiteValidation {
  return {
    timestamp: new Date(udmiMessage.data.timestamp),
    siteName: udmiMessage.attributes.deviceRegistryId,
    message: udmiMessage.data,
  };
}
