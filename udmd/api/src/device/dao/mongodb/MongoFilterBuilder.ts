import { Filter } from '../../../device/model';

export function getFilter(jsonFilters: Filter[]): any {
  // default the mongo filters to an empty object, this can be passed into the db.collection(...).find() method without issues
  const mongoFilters: any = {};

  jsonFilters.forEach((filter) => {
    // '~' is our symbol for 'contains'
    if (filter.operator === '~') {
      // this means we need to do a case insensitive regex match for the value of the field
      mongoFilters[filter.field] = { $regex: filter.value, $options: 'i' };
    }
  });

  return mongoFilters;
}
