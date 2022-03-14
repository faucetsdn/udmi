import { SortOptions, SORT_DIRECTION } from '../../../../device/model';
import { getSort } from '../../../../device/dao/mongodb/MongoSortBuilder';

describe('MongoFilterBuilder.getFilter', () => {
  test('returns a sort ASC object with the direction set', async () => {
    expect(getSort({ field: 'make', direction: SORT_DIRECTION.ASC })).toEqual({ make: 1, name: 1 });
  });
  test('returns a sort DESC object with the direction set', async () => {
    expect(getSort({ field: 'model', direction: SORT_DIRECTION.DESC })).toEqual({ model: -1, name: -1 });
  });
});
