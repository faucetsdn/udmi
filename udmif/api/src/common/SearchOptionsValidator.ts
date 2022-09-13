import { isNil } from 'lodash';
import { logger } from './logger';
import { SearchOptions, DistinctSearchOptions, ValidatedDistinctSearchOptions, ValidatedSearchOptions } from './model';

export function validateSearchOptions(searchOptions?: SearchOptions): ValidatedSearchOptions {
  const { offset } = searchOptions ?? {};

  if (isNil(offset)) {
    logger.warn('An offset was not provided, defaulting to 0');
  }

  return {
    ...searchOptions,
    offset: offset ?? 0, // default to 0
  };
}

export function validateDistinctSearchOptions(searchOptions?: DistinctSearchOptions): ValidatedDistinctSearchOptions {
  const { limit } = searchOptions ?? {};

  if (isNil(limit)) {
    logger.warn('A limit was not provided, defaulting to 10');
  }

  return {
    ...searchOptions,
    limit: limit ?? 10, // default to 10
  };
}
