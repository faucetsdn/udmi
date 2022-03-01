import { Device, SearchOptions } from './model';

export function batchDevices(devices: Device[], filter: SearchOptions): Device[] {
  if (filter.batchSize == 0) {
    throw Error('A batch size greater than zero must be provided.');
  }

  const deviceCount = devices.length;
  if (filter.offset > deviceCount) {
    throw Error('An invalid offset that is greater than the total number of devices was provided.');
  }

  return devices.slice(filter.offset, filter.offset + filter.batchSize);
}
