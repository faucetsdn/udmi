import {
  DeviceMakesSearchOptions,
  DeviceModelsSearchOptions,
  DeviceNamesSearchOptions,
  SearchOptions,
  SectionsSearchOptions,
  SiteNamesSearchOptions,
} from '../../device/model';
import {
  validate,
  validateDeviceMakesSearchOptions,
  validateDeviceModelsSearchOptions,
  validateDeviceNamesSearchOptions,
  validateSectionsSearchOptions,
  validateSiteNamesSearchOptions,
} from '../../device/SearchOptionsValidator';

describe('SearchOptionsValidator.validate', () => {
  test('defaults offset if not provided', () => {
    expect(validate({ batchSize: 100 })).toEqual({ batchSize: 100, offset: 0 });
  });

  test.each([
    [0, 0],
    [999, 999],
    [1000, 1000],
    [1001, 1000],
  ])('limit is reduced to 1000 if a value greater than 1000', async (batchSize, expected) => {
    const searchOptions: SearchOptions = { batchSize, offset: 0 };

    const expectedSearchOption: SearchOptions = { batchSize: expected, offset: 0 };

    expect(validate(searchOptions)).toEqual(expectedSearchOption);
  });
});

describe('SearchOptionsValidator.validateDeviceNamesSearchOptions', () => {
  test.each([
    [0, 0],
    [null, 10],
    [undefined, 10],
  ])('limit defaults to 10 when not supplied', async (limit, expected) => {
    const searchOptions: DeviceNamesSearchOptions = { limit };
    expect(validateDeviceNamesSearchOptions(searchOptions).limit).toEqual(expected);
  });

  test('limit still defaults to 10 when no searchOptions are supplied', () => {
    expect(validateDeviceNamesSearchOptions().limit).toEqual(10);
  });
});

describe('SearchOptionsValidator.validateDeviceMakesSearchOptions', () => {
  test.each([
    [0, 0],
    [null, 10],
    [undefined, 10],
  ])('limit defaults to 10 when not supplied', async (limit, expected) => {
    const searchOptions: DeviceMakesSearchOptions = { limit };
    expect(validateDeviceMakesSearchOptions(searchOptions).limit).toEqual(expected);
  });

  test('limit still defaults to 10 when no searchOptions are supplied', () => {
    expect(validateDeviceMakesSearchOptions().limit).toEqual(10);
  });
});

describe('SearchOptionsValidator.validateDeviceModelsSearchOptions', () => {
  test.each([
    [0, 0],
    [null, 10],
    [undefined, 10],
  ])('limit defaults to 10 when not supplied', async (limit, expected) => {
    const searchOptions: DeviceModelsSearchOptions = { limit };
    expect(validateDeviceModelsSearchOptions(searchOptions).limit).toEqual(expected);
  });

  test('limit still defaults to 10 when no searchOptions are supplied', () => {
    expect(validateDeviceModelsSearchOptions().limit).toEqual(10);
  });
});

describe('SearchOptionsValidator.validateSiteNamesSearchOptions', () => {
  test.each([
    [0, 0],
    [null, 10],
    [undefined, 10],
  ])('limit defaults to 10 when not supplied', async (limit, expected) => {
    const searchOptions: SiteNamesSearchOptions = { limit };
    expect(validateSiteNamesSearchOptions(searchOptions).limit).toEqual(expected);
  });

  test('limit still defaults to 10 when no searchOptions are supplied', () => {
    expect(validateSiteNamesSearchOptions().limit).toEqual(10);
  });
});

describe('SearchOptionsValidator.validateSectionsSearchOptions', () => {
  test.each([
    [0, 0],
    [null, 10],
    [undefined, 10],
  ])('limit defaults to 10 when not supplied', async (limit, expected) => {
    const searchOptions: SectionsSearchOptions = { limit };
    expect(validateSectionsSearchOptions(searchOptions).limit).toEqual(expected);
  });

  test('limit still defaults to 10 when no searchOptions are supplied', () => {
    expect(validateSectionsSearchOptions().limit).toEqual(10);
  });
});
