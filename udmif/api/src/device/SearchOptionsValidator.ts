import { logger } from '../common/logger';
import {
  DeviceMakesSearchOptions,
  DeviceModelsSearchOptions,
  DeviceNamesSearchOptions,
  SearchOptions,
  SectionsSearchOptions,
  SitesSearchOptions,
} from './model';

export function validate(searchOptions: SearchOptions): SearchOptions {
  const validatedSearchOption = searchOptions;

  const offset = validatedSearchOption.offset;
  if (offset === undefined) {
    logger.warn(`An offset was not provided, defaulting to 0`);
    validatedSearchOption.offset = 0;
  }

  const batchSize: number = validatedSearchOption.batchSize;
  if (batchSize > 1000) {
    logger.warn(`The batch size ${batchSize} exceeds max of 1000, restricting to 1000 records`);
    validatedSearchOption.batchSize = 1000;
  }

  return validatedSearchOption;
}

export function validateDeviceNamesSearchOptions(searchOptions: DeviceNamesSearchOptions): DeviceNamesSearchOptions {
  const limit = validateLimitSearchOption(searchOptions.limit);

  return {
    ...searchOptions,
    limit,
  };
}

export function validateDeviceMakesSearchOptions(searchOptions: DeviceMakesSearchOptions): DeviceMakesSearchOptions {
  const limit = validateLimitSearchOption(searchOptions.limit);

  return {
    ...searchOptions,
    limit,
  };
}

export function validateDeviceModelsSearchOptions(searchOptions: DeviceModelsSearchOptions): DeviceModelsSearchOptions {
  const limit = validateLimitSearchOption(searchOptions.limit);

  return {
    ...searchOptions,
    limit,
  };
}

export function validateSitesSearchOptions(searchOptions: SitesSearchOptions): SitesSearchOptions {
  const limit = validateLimitSearchOption(searchOptions.limit);

  return {
    ...searchOptions,
    limit,
  };
}

export function validateSectionsSearchOptions(searchOptions: SectionsSearchOptions): SectionsSearchOptions {
  const limit = validateLimitSearchOption(searchOptions.limit);

  return {
    ...searchOptions,
    limit,
  };
}

function validateLimitSearchOption(limit?: number): number {
  let validatedLimit: number = limit;

  if (!limit) {
    logger.warn('A limit was not provided, defaulting to 10');
    validatedLimit = 10;
  }

  return validatedLimit;
}
