import { Filter } from '../../../../device/model';
import { getFilter } from '../../../../device/dao/mongodb/MongoFilterBuilder';

const emptyMongoFilter = {};

const filters = [
  [getContainsFilter('make', 'value'), getExpectedRegex('make', 'value')],
  [getContainsFilter('model', 'value'), getExpectedRegex('model', 'value')],
  [getEqualsFilter('make', 'value'), emptyMongoFilter],
];

describe('MongoFilterBuilder.getFilter', () => {
  test.each(filters)(
    'returns a regex filter object when a ~ is the operator',
    async (filter: Filter, expectedResult) => {
      const jsonFilters: Filter[] = [filter];
      expect(getFilter(jsonFilters)).toEqual(expectedResult);
    }
  );
});

function getContainsFilter(field: string, value: string): Filter {
  return { field, operator: '~', value };
}

function getEqualsFilter(field: string, value: string): Filter {
  return { field, operator: '=', value };
}

function getExpectedRegex(field: string, value: string): any {
  const regex = {};
  regex[field] = { $regex: value, $options: 'i' };
  return regex;
}
