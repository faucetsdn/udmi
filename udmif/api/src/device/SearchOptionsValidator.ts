import { logger } from '../common/logger';
import {
  CommonSearchOptions,
  DeviceMakesSearchOptions,
  DeviceModelsSearchOptions,
  DeviceNamesSearchOptions,
  SearchOptions,
  SectionsSearchOptions,
  SitesSearchOptions,
  ValidatedCommonSearchOptions,
  ValidatedDeviceMakesSearchOptions,
  ValidatedDeviceModelsSearchOptions,
  ValidatedDeviceNamesSearchOptions,
  ValidatedSectionsSearchOptions,
  ValidatedSitesSearchOptions,
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

export function validateDeviceNamesSearchOptions(
  searchOptions?: DeviceNamesSearchOptions
): ValidatedDeviceNamesSearchOptions {
  return {
    ...validateCommonSearchOptions(searchOptions),
  };
}

export function validateDeviceMakesSearchOptions(
  searchOptions?: DeviceMakesSearchOptions
): ValidatedDeviceMakesSearchOptions {
  return {
    ...validateCommonSearchOptions(searchOptions),
  };
}

export function validateDeviceModelsSearchOptions(
  searchOptions?: DeviceModelsSearchOptions
): ValidatedDeviceModelsSearchOptions {
  return {
    ...validateCommonSearchOptions(searchOptions),
  };
}

export function validateSitesSearchOptions(searchOptions?: SitesSearchOptions): ValidatedSitesSearchOptions {
  return {
    ...validateCommonSearchOptions(searchOptions),
  };
}

export function validateSectionsSearchOptions(searchOptions?: SectionsSearchOptions): ValidatedSectionsSearchOptions {
  return {
    ...validateCommonSearchOptions(searchOptions),
  };
}

function validateCommonSearchOptions(searchOptions?: CommonSearchOptions): ValidatedCommonSearchOptions {
  const { search, limit } = searchOptions ?? {};

  return {
    limit: limit ?? 10, // default to 10
    search,
  };
}
