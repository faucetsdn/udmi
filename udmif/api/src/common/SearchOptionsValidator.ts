import { logger } from './logger';
import { SearchOptions, DistinctSearchOptions, ValidatedDistinctSearchOptions, ValidatedSearchOptions } from './model';

export function validateSearchOptions(searchOptions: SearchOptions): ValidatedSearchOptions {
  let { offset, batchSize } = searchOptions;

  if (offset === undefined) {
    logger.warn('An offset was not provided, defaulting to 0');
    offset = 0;
  }

  if (batchSize > 1000) {
    logger.warn(`The batch size ${batchSize} exceeds max of 1000, restricting to 1000 records`);
    batchSize = 1000;
  }

  return {
    ...searchOptions,
    offset,
    batchSize,
  };
}

export function validateDistinctSearchOptions(searchOptions?: DistinctSearchOptions): ValidatedDistinctSearchOptions {
  const { search, limit } = searchOptions ?? {};

  return {
    limit: limit ?? 10, // default to 10
    search,
  };
}
