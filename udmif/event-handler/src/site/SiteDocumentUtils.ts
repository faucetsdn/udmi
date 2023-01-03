import { Validation } from '../model/Validation';
import { ValidationEvent } from '../udmi/UdmiEvent';
import { Site, SiteValidation } from './model/Site';

export function getSiteDocument(originalSite: Site, udmiEvent: ValidationEvent): Site {
  const name = udmiEvent.attributes.deviceRegistryId;

  let validation;
  if (!originalSite) {
    validation = udmiEvent.data;
  } else {
    validation = mergeValidations(originalSite.validation, udmiEvent.data);
  }

  return { name, validation };
}

export function getSiteValidationDocument(udmiEvent: ValidationEvent): SiteValidation {
  return {
    timestamp: new Date(udmiEvent.data.timestamp),
    siteName: udmiEvent.attributes.deviceRegistryId,
    message: udmiEvent.data,
  };
}

export function mergeValidations(originalValidation: Validation, incomingValidation: Validation): any {
  let mergedValidation: any = {};
  Object.keys(originalValidation).forEach(
    (key) => (mergedValidation[key] = incomingValidation[key] ? incomingValidation[key] : originalValidation[key])
  );
  return mergedValidation;
}
