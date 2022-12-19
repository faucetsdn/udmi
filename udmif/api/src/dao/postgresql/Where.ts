import { Filter } from '../../common/model';
import { fromString } from '../../common/FilterParser';
import { Knex } from 'knex';

// list of possible columns to search on
const filterableColumns = ['name', 'make', 'model', 'site', 'section'];

export function getWhereOption(filter, builder: Knex.QueryBuilder<any, any[]>): Knex.QueryBuilder<any, any[]> {
  getWhereOptions(filter).forEach((filter) => {
    const field = filter.field;
    builder.andWhere((queryBuilder) => {
      if (filter.inFields.length > 0) {
        queryBuilder.whereIn(field, filter.inFields);
      }
      filter.likeFields.forEach((value) => queryBuilder.orWhereLike(field, value));
    });
  });

  return builder;
}

export function getWhereOptions(filter: string): Array<any> {
  if (!filter) {
    return [];
  }

  const filterMap = getMapOfFiltersByField(fromString(filter));

  console.log('filterMap: ' + JSON.stringify(filterMap));

  const convertedFilters = convertMapOfFiltersToFilter(filterMap);

  console.log('convertedFilters: ' + JSON.stringify(convertedFilters));

  return convertedFilters;
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
        // convert the filter to an array of values
        let operator = '';
        const likeFields: string[] = [];
        const inFields: string[] = [];
        // if (mapOfFilters[column].length === 1) {
        // our filter is a single filter on a column, we can't use an to represent the options
        // const filter = mapOfFilters[column][0];
        // operator = filter.operator === '~' ? 'like' : '=';
        // const value = operator === 'like' ? `%${filter.value}%` : filter.value;
        // return { field: column, operator, values: value };
        // } else {
        mapOfFilters[column].forEach((filter: Filter) => {
          if (filter.operator === '~') {
            likeFields.push(`%${filter.value}%`);
          } else {
            inFields.push(filter.value);
          }
        });
        return { field: column, likeFields, inFields };
        // }
      }
    })
    .filter((expression) => expression);
}
