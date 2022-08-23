import { fromString } from '../../common/FilterParser';
import { Filter } from '../../common/model';

describe('FilterParser.fromString', () => {
  test('converts a string into a filter object', () => {
    const filter: Filter[] = fromString('[{"name":"name1","operator":"=","value":"someVal"}]');
    expect(filter[0]).toEqual({ name: 'name1', operator: '=', value: 'someVal' });
  });
});
