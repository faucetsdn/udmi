import { Device, SearchOptions, SORT_DIRECTION } from '../../device/model';

export function createDevices(count: number): Device[] {
  const devices: Device[] = [];
  let n = 1;
  while (n <= count) {
    const name = n % 2 == 0 ? `AHU-${n}` : `CDS-${n}`;
    const make: string = `make-${n}`;
    const model: string = n % 3 == 0 ? `AAAA-${n}` : `BBBB-${n}`;
    const site: string = `SG-SIN-MBC${n}`;
    const section: string = `SIN-MBC${n}`;
    const lastPayload: string = '2022-08-30';
    const operational: boolean = n % 3 == 0 ? false : true;
    devices.push({ name, make, model, site, section, lastPayload, operational });
    n++;
  }

  return devices;
}

export function createSearchOptions(
  batchSize: number = 10,
  offset: number = 10,
  direction: SORT_DIRECTION = SORT_DIRECTION.ASC,
  filter?: string
): SearchOptions {
  return {
    batchSize,
    offset,
    sortOptions: { direction, field: 'name' },
  };
}
