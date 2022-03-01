import { Device, SearchOptions, SORT_DIRECTION } from './model';

export function batchDevices(devices: Device[], filter: SearchOptions): Device[] {
  if (filter.batchSize == 0) {
    throw Error('A batch size greater than zero must be provided.');
  }

  const deviceCount = devices.length;
  if (filter.offset > deviceCount) {
    throw Error('An invalid offset that is greater than the total number of devices was provided.');
  }

  // a device name = value18 is sorted earlier than a device = value2, need to fix this
  devices.sort(compare(filter.sortOptions.field, filter.sortOptions.direction));

  return devices.slice(filter.offset, filter.offset + filter.batchSize);
}

function compare(field: string, direction: SORT_DIRECTION) {
  return function (a, b) {
    if (direction.toString() === 'ASC') {
      return a[field].localeCompare(b[field]);
    } else {
      return b[field].localeCompare(a[field]);
    }
  };
}
