import { SORT_DIRECTION } from '../../common/model';
import { getSort } from '../../mongo/MongoSortBuilder';

describe('MongoSortBuilder.getSort', () => {
  test('returns a sort ASC object with the direction set', () => {
    expect(getSort({ field: 'make', direction: SORT_DIRECTION.ASC })).toEqual({ make: 1, name: 1 });
  });
  test('returns a sort DESC object with the direction set', () => {
    expect(getSort({ field: 'model', direction: SORT_DIRECTION.DESC })).toEqual({ model: -1, name: -1 });
  });
});
