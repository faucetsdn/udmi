import { validateSearchOptions, validateDistinctSearchOptions } from '../../common/SearchOptionsValidator';

describe('SearchOptionsValidator.validateSearchOptions', () => {
  test('defaults offset if not provided', () => {
    expect(validateSearchOptions({ batchSize: 100 })).toEqual({ batchSize: 100, offset: 0 });
  });

  test.each([
    [0, 0],
    [999, 999],
    [1000, 1000],
    [1001, 1000],
  ])('limit is reduced to 1000 if a value greater than 1000', async (batchSize, expected) => {
    expect(validateSearchOptions({ batchSize })).toEqual({ batchSize: expected, offset: 0 });
  });
});

describe('SearchOptionsValidator.validateDistinctSearchOptions', () => {
  test.each([
    [0, 0],
    [null, 10],
    [undefined, 10],
  ])('limit defaults to 10 when not supplied', async (limit, expected) => {
    expect(validateDistinctSearchOptions({ limit }).limit).toEqual(expected);
  });

  test('limit still defaults to 10 when no searchOptions are supplied', () => {
    expect(validateDistinctSearchOptions().limit).toEqual(10);
  });
});
