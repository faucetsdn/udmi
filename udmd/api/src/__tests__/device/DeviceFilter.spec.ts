import { SearchOptions, SORT_DIRECTION } from '../../device/model';
import { Device } from '../../device/model';
import { filterDevices } from '../../device/DeviceFilter';
import { createDevices, createSearchOptions } from './data';

describe('DeviceFilter.filterDevices', () => {
  const devices: Device[] = createDevices(100);
  const batchSize = 10;
  const offset = 10;

  test('the number of Devices returned matches the batchSize submitted', () => {
    const batchSize = 10;
    const searchOptions: SearchOptions = createSearchOptions();

    expect(filterDevices(devices, searchOptions).length).toEqual(batchSize);
  });

  test('the matching Devices start at the offset specified', () => {
    const batchSize = 20;
    const offset = 20;

    const searchOptions: SearchOptions = createSearchOptions(batchSize, offset);

    expect(filterDevices(devices, searchOptions)).toEqual(devices.slice(offset, offset + batchSize));
  });
});
