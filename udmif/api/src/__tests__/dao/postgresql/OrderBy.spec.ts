import { SORT_DIRECTION, ValidatedSearchOptions } from '../../../common/model';
import { getOrderByOptions } from '../../../dao/postgresql/OrderBy';

describe('OrderBy', () => {
  test('it creates an empty order by array when no sorting/ordering specified', () => {
    expect(getOrderByOptions(null)).toEqual([]);
  });

  test('it creates an order by array when sorting/ordering specified and sets the correct asc order', () => {
    expect(getOrderByOptions({ direction: SORT_DIRECTION.ASC, field: 'name' })).toEqual([
      { column: 'name', order: 'asc' },
    ]);
  });

  test('it creates an order by array when sorting/ordering specified and sets the correct desc order', () => {
    expect(getOrderByOptions({ direction: SORT_DIRECTION.DESC, field: 'name' })).toEqual([
      { column: 'name', order: 'desc' },
    ]);
  });
});
