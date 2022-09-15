import { fromString } from '../common/FilterParser';
import { ValidatedDistinctSearchOptions } from '../common/model';
import { getFilter } from './MongoFilterBuilder';

// Builds a query which will return distinct strings based on a field of interest.
export function getAggregate(field: string, searchOptions: ValidatedDistinctSearchOptions): any {
  return [
    {
      $match: {
        ...(searchOptions.filter ? getFilter(fromString(searchOptions.filter)) : {}),
        [field]: { $in: [new RegExp(searchOptions.search, 'i')] },
      },
    },
    { $group: { _id: `$${field}`, distinct_doc: { $first: '$$ROOT' } } },
    {
      $replaceRoot: {
        newRoot: '$distinct_doc',
      },
    },
    { $limit: searchOptions.limit },
    { $sort: { [field]: 1 } },
  ];
}
