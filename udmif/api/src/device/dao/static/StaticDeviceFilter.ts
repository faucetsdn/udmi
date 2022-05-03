import { Device, Filter } from '../../model';

export function filterDevices(filters: Filter[], devices: Device[]): Device[] {
  let filteredDevices = devices;

  filters.forEach((filter) => {
    filteredDevices = filteredDevices.filter((device) => filterIncludes(device, filter.field, filter.value));
  });

  return filteredDevices;
}

function filterIncludes(device, field: string, value: string): boolean {
  return device[field].includes(value);
}
