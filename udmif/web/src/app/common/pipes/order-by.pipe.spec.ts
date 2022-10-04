import { OrderByPipe } from './order-by.pipe';

describe('OrderByPipe', () => {
  const pipe = new OrderByPipe();

  const testCases = [
    { expected: [] },
    { value: null, sortBy: null, order: null, expected: [] },
    {
      value: [{ name: 'device-1' }, { name: 'device-3' }, { name: 'device-2' }],
      sortBy: 'name',
      expected: [{ name: 'device-1' }, { name: 'device-2' }, { name: 'device-3' }],
    },
    {
      value: [{ name: 'device-1' }, { name: 'device-3' }, { name: 'device-2' }],
      sortBy: 'name',
      order: 'asc',
      expected: [{ name: 'device-1' }, { name: 'device-2' }, { name: 'device-3' }],
    },
    {
      value: [{ name: 'device-1' }, { name: 'device-3' }, { name: 'device-2' }],
      sortBy: 'name',
      order: 'desc',
      expected: [{ name: 'device-3' }, { name: 'device-2' }, { name: 'device-1' }],
    },
    {
      value: [{ name: 'device-1' }, { name: 'device-3' }, { name: 'device-2' }],
      sortBy: 'bogus',
      expected: [{ name: 'device-1' }, { name: 'device-3' }, { name: 'device-2' }],
    },
    {
      value: [{ name: 'device-1' }, { name: 'device-3' }, { name: 'device-2' }],
      sortBy: 'name',
      order: 'bogus',
      expected: [{ name: 'device-1' }, { name: 'device-2' }, { name: 'device-3' }],
    },
  ];

  testCases.forEach((test) => {
    it(`should transform ${test.value} to ${test.expected}`, () => {
      expect(pipe.transform(test.value, test.sortBy, test.order as 'asc' | 'desc')).toEqual(test.expected);
    });
  });
});
