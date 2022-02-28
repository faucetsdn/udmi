import { Device, SearchOptions } from './model';

export function filterDevices(devices: Device[], filter: SearchOptions): Device[] {
  return devices.slice(filter.offset, filter.offset + filter.batchSize);
}
