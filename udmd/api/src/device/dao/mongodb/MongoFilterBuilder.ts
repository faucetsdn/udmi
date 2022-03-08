import { Filter } from '../../../device/model';

export function getFilter(jsonFilters: Filter[]): any {
  const mongoFilters: any = {};

  jsonFilters.forEach((filter) => {
    // '~' means contains with means we need to use a regex and perform a case insensitive match for a value containing a string
    if (filter.operator === '~') {
      mongoFilters[filter.field] = { $regex: filter.value, $options: 'i' };
    }
  });

  console.log(mongoFilters);
  return mongoFilters;
}
