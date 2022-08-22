import { logger } from '../common/logger';
import {
  CommonSearchOptions,
  SearchOptions,
  SiteNamesSearchOptions,
  ValidatedCommonSearchOptions,
  ValidatedSiteNamesSearchOptions,
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

export function validateSiteNamesSearchOptions(
  searchOptions?: SiteNamesSearchOptions
): ValidatedSiteNamesSearchOptions {
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
