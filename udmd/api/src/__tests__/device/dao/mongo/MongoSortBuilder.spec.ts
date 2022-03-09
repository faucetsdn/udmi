import { SortOptions, SORT_DIRECTION } from '../../../../device/model';
import { getSort } from '../../../../device/dao/mongodb/MongoSortBuilder';

const filters = [
  [{ field: 'make', direction: SORT_DIRECTION.ASC }, getExpectedSort('make', 1)],
  [{ field: 'model', direction: SORT_DIRECTION.DESC }, getExpectedSort('model', -1)],
];

describe('MongoFilterBuilder.getFilter', () => {
  test.each(filters)(
    'returns a sort object with the direction set',
    async (sortOptions: SortOptions, expectedResult) => {
      expect(getSort(sortOptions)).toEqual(expectedResult);
    }
  );
});

function getExpectedSort(field: string, direction: number): any {
  const sort = {};
  sort[field] = direction;
  sort['name'] = direction;
  return sort;
}
