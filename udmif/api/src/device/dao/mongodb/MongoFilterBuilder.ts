import { Filter } from '../../../device/model';

// list of possible columns to search on
const filterableColumns = ['name', 'make', 'model', 'site', 'section'];

export function getFilter(filters: Filter[]): any {
  return convertMapOfFiltersToFilter(getMapOfFiltersByField(filters));
}

// arrange the filters into a map { make : filter[], site: filter[] }
function getMapOfFiltersByField(filters: Filter[]): Map<string, Filter[]> {
  return filters.reduce((result, filter) => {
    const existingfilters = result[filter.field] || [];
    return {
      ...result,
      [filter.field]: [...existingfilters, filter],
    };
  }, new Map<string, Filter[]>());
}

function convertMapOfFiltersToFilter(mapOfFilters: Map<string, Filter[]>): any {
  const filterExpressions = filterableColumns
    .map((column) => {
      if (mapOfFilters[column]) {
        const inArray = mapOfFilters[column].map((filter: Filter) =>
          filter.operator === '~' ? new RegExp(filter.value, 'i') : filter.value
        );
        return { [column]: { $in: inArray } };
      }
    })
    .filter((expression) => expression);

  return { $or: filterExpressions };
}
