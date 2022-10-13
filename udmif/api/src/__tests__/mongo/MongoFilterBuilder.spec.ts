import { Filter } from '../../common/model';
import { getFilter } from '../../mongo/MongoFilterBuilder';

describe('MongoFilterBuilder.getFilter', () => {
  test('multiple contains filters on the different field results in a ANDed filter where the same field uses an in clause', () => {
    const filters: Filter[] = [
      getContainsFilter('make', 'val1'),
      getContainsFilter('make', 'val2'),
      getContainsFilter('model', 'val2'),
      getContainsFilter('site', 'val3'),
      getEqualsFilter('make', 'val4'),
    ];
    expect(getFilter(filters)).toEqual({
      $and: [
        {
          make: { $in: [/val1/i, /val2/i, 'val4'] },
        },
        {
          model: { $in: [/val2/i] },
        },
        {
          site: { $in: [/val3/i] },
        },
      ],
    });
  });
});

function getContainsFilter(field: string, value: string): Filter {
  return { field, operator: '~', value };
}

function getEqualsFilter(field: string, value: string): Filter {
  return { field, operator: '=', value };
}
