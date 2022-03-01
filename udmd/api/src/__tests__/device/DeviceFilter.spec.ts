import { SearchOptions, SORT_DIRECTION } from '../../device/model';
import { Device } from '../../device/model';
import { batchDevices } from '../../device/DeviceFilter';
import { createDevices, createSearchOptions } from './data';

describe('DeviceFilter.filterDevices', () => {
  const devices: Device[] = createDevices(100);

  test('the number of Devices returned matches the batchSize submitted', () => {
    const batchSize = 10;
    const searchOptions: SearchOptions = createSearchOptions();

    expect(batchDevices(devices, searchOptions).length).toEqual(batchSize);
  });

  test('the matching Devices start at the offset specified', () => {
    const batchSize = 20;
    const offset = 20;

    const searchOptions: SearchOptions = createSearchOptions(batchSize, offset);
    expect(batchDevices(devices, searchOptions)).toEqual(devices.slice(offset, offset + batchSize));
  });

  test('that providing an offset greater than the number of devices throws an error', () => {
    const batchSize = 20;
    const offset = 200;

    const searchOptions: SearchOptions = createSearchOptions(batchSize, offset);

    expect(() => {
      batchDevices(devices, searchOptions);
    }).toThrow('An invalid offset that is greater than the total number of devices was provided.');
  });

  test('that if a batch size of 0 is provided, an error is thrown', () => {
    const batchSize = 0;

    const searchOptions: SearchOptions = createSearchOptions(batchSize);

    expect(() => {
      batchDevices(devices, searchOptions);
    }).toThrow('A batch size greater than zero must be provided');
  });
});
