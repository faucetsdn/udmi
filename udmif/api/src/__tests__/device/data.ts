import { Device, Point } from '../../device/model';
import { SearchOptions, SORT_DIRECTION } from '../../common/model';
import deviceMessage from './deviceValidationMessage.json';

export function createDevices(count: number): Device[] {
  const devices: Device[] = [];
  let n = 1;
  while (n <= count) {
    const uuid = '00000000-0000-0000-0000-000000000' + pad(n);
    const id = '00000000-0000-0000-0000-000000000' + pad(n);
    const name = n % 2 === 0 ? `CDS-${n}` : `AHU-${n}`;
    const make: string = `make-${n}`;
    const model: string = n % 3 === 0 ? `AAAA-${n}` : `BBBB-${n}`;
    const site: string = n % 2 === 0 ? `SITE-${n}` : `LOC-${n}`;
    const section: string = `SIN-MBC${n}`;
    const lastPayload: string = '2022-08-30';
    const operational: boolean = n % 3 === 0 ? false : true;
    const serialNumber: string = `serialNo-${n}`;
    const firmware: string = `v-${n}`;
    const validation: any = deviceMessage;

    const points: Point[] = createPoints(5);

    devices.push({
      uuid,
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
      points,
      validation,
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
    const units: string = i % 2 === 0 ? `units-${i}` : null;
    const state: string = states[i];
    return { id, name, value, units, state };
  });
}

const pointTemplate: [{}] = [{ id: '', name: '', value: '', units: '' }];

const states: string[] = ['Applied', 'Updating', 'Overriden', 'Invalid', 'Failure'];

function pad(num) {
  let s = '000' + num;
  return s.substring(s.length - 3);
}
