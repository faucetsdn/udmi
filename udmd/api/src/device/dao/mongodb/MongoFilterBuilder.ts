import { Filter } from '../../../device/model';

export function getFilter(jsonFilters: Filter[]): any {
  // default the mongo filters to an empty object, this can be passed into the db.collection(...).find() method without issues
  const mongoFilters: any = {};

  jsonFilters.forEach((filter) => {
    if (filter.field === 'operational') {
      // do nothing for filtering on the operational boolean yet
      // TODO: resolve in a future PR
    }
    // '~' is our symbol for 'contains'
    else if (filter.operator === '~') {
      // this means we need to do a case insensitive regex match for the value of the field
      mongoFilters[filter.field] = { $regex: filter.value, $options: 'i' };
    } else if (filter.operator === '=') {
      // this means we need to do an exact match for the value of the field
      mongoFilters[filter.field] = filter.value;
    }
  });

  return mongoFilters;
}
