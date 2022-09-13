import { validateSearchOptions, validateDistinctSearchOptions } from '../../common/SearchOptionsValidator';

describe('SearchOptionsValidator.validateSearchOptions', () => {
  test.each([
    [0, 0],
    [null, 0],
    [undefined, 0],
  ])('offset defaults to 0 when not supplied', async (offset, expected) => {
    expect(validateSearchOptions({ offset }).offset).toEqual(expected);
  });

  test('offset still defaults to 0 when no searchOptions are supplied', () => {
    expect(validateSearchOptions().offset).toEqual(0);
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
