import { SearchOptions } from '../../device/model';
import { validate } from '../../device/SearchOptionsValidator';

describe('SearchOptionsValidator.validate', () => {
  test('defaults offset if not provided', () => {
    expect(validate({ batchSize: 100 })).toEqual({ batchSize: 100, offset: 0 });
  });

  test.each([
    [999, 999],
    [1000, 1000],
    [1001, 1000],
  ])('limit is reduced to 1000 if a value greater than 1000', async (batchSize, expected) => {
    const searchOptions: SearchOptions = { batchSize, offset: 0 };

    const expectedSearchOption: SearchOptions = { batchSize: expected, offset: 0 };

    expect(validate(searchOptions)).toEqual(expectedSearchOption);
  });
});
