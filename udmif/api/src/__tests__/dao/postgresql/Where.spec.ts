import { Filter } from '../../../common/model';
import { getWhereOptions } from '../../../dao/postgresql/Where';

describe('Where', () => {
  test('is creates an empty where statement', () => {
    const result = getWhereOptions(null);
    expect(result).toEqual([]);
  });

  test('is creates an empty where statement', () => {
    const filter: Filter[] = [];
    const result = getWhereOptions(JSON.stringify(filter));
    expect(result).toEqual([]);
  });

  test('is creates an populated where statement', () => {
    const filter: Filter[] = [
      { field: 'name', operator: '~', value: 'test1' },
      { field: 'site', operator: '~', value: 'test2' },
      { field: 'site', operator: '=', value: 'site1' },
      { field: 'site', operator: '=', value: 'site2' },
    ];
    const result = getWhereOptions(JSON.stringify(filter));
    expect(result).toEqual([
      { field: 'name', operator: 'like', values: ['%test1%'] },
      { field: 'site', operator: 'in', values: ['%test2%', 'site1', 'site2'] },
    ]);
  });
});
