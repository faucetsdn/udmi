import { Filter } from '../../common/model';
import { fromString } from '../../common/FilterParser';

// list of possible columns to search on
const filterableColumns = ['name', 'make', 'model', 'site', 'section'];

export function getWhereOptions(filter: string): Array<any> {
  if (!filter) {
    return [];
  }

  const filterMap = getMapOfFiltersByField(fromString(filter));

  return convertMapOfFiltersToFilter(filterMap);
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

function convertMapOfFiltersToFilter(mapOfFilters: Map<string, Filter[]>): Array<any> {
  // using the preset list of columns
  return filterableColumns
    .map((column) => {
      // match if the map contains the column
      if (mapOfFilters[column]) {
        // convetr the filter to an array of values
        let operator = '';
        const inArray = mapOfFilters[column].map((filter: Filter) => {
          operator = filter.operator === '~' ? 'like' : 'in';
          return operator === 'like' ? `%${filter.value}%` : filter.value;
        });
        return { field: column, operator, values: inArray };
      }
    })
    .filter((expression) => expression);
}
