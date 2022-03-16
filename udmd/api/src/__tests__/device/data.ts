import { Device, Point, SearchOptions, SORT_DIRECTION } from '../../device/model';

export function createDevices(count: number): Device[] {
  const devices: Device[] = [];
  let n = 1;
  while (n <= count) {
    const id = '00000000-0000-0000-0000-000000000' + pad(n);
    const name = n % 2 == 0 ? `AHU-${n}` : `CDS-${n}`;
    const make: string = `make-${n}`;
    const model: string = n % 3 == 0 ? `AAAA-${n}` : `BBBB-${n}`;
    const site: string = `SG-SIN-MBC${n}`;
    const section: string = `SIN-MBC${n}`;
    const lastPayload: string = '2022-08-30';
    const operational: boolean = n % 3 == 0 ? false : true;
    const serialNumber: string = `serialNo-${n}`;
    const firmware: string = `v-${n}`;
    const tags: string[] = [];

    const points: Point[] = createPoints(5);

    devices.push({
      id,
      name,
      make,
      model,
      site,
      section,
      lastPayload,
      operational,
      serialNumber,
      firmware,
      tags,
      points,
    });
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

export function createPoints(count: number): Point[] {
  return [...Array(count)].map((_, i) => {
    const id: string = `id-${i}`;
    const name: string = `point-${i}`;
    const value: string = `value-${i}`;
    const units: string = `units-${i}`;
    const state: string = states[i];
    return { id, name, value, units, state };
  });
}

const pointTemplate: [{}] = [{ id: '', name: '', value: '', units: '' }];

const states: string[] = ['Applied', 'Updating', 'Overriden', 'Invalid', 'Failure'];

function pad(num) {
  var s = '000' + num;
  return s.substring(s.length - 3);
}
