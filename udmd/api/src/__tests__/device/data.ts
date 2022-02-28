import { Device, SearchOptions, SORT_DIRECTION } from '../../device/model';

export function createDevices(count: number): Device[] {
  const devices: Device[] = [];
  let n = 1;
  while (n <= count) {
    const id = `id${n}`;
    const name = `name${n}`;
    devices.push({ id, name });
    n++;
  }

  return devices;
}

export function createSearchOptions(
  batchSize: number = 10,
  offset: number = 10,
  direction: SORT_DIRECTION = SORT_DIRECTION.DESC,
  filter: string = 'name'
): SearchOptions {
  return {
    batchSize,
    offset,
    sortOptions: { direction, field: 'field' },
    filter,
  };
}
