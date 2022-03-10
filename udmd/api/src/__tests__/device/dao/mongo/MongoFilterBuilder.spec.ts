import { Filter } from '../../../../device/model';
import { getFilter } from '../../../../device/dao/mongodb/MongoFilterBuilder';

const emptyMongoFilter = {};

const containfilters = [
  [getContainsFilter('make', 'value'), getExpectedRegex('make', 'value')],
  [getContainsFilter('model', 'value'), getExpectedRegex('model', 'value')],
  [getContainsFilter('operational', 'true'), emptyMongoFilter],
  [getEqualsFilter('make', 'value'), getExpectedEqualsExpression('make', 'value')],
];

describe('MongoFilterBuilder.getFilter', () => {
  test.each(containfilters)(
    'returns a regex filter object when ~ is the operator',
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

function getExpectedEqualsExpression(field: string, value: string): any {
  const equalsExpression = {};
  equalsExpression[field] = value;
  return equalsExpression;
}

function getExpectedRegex(field: string, value: string): any {
  const regex = {};
  regex[field] = { $regex: value, $options: 'i' };
  return regex;
}
