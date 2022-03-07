import { randomInt } from 'crypto';
import { Device, SearchOptions, SORT_DIRECTION } from '../model';
import { DeviceDAO } from './DeviceDAO';

// this class exists to return static, sorted, and filtered data
export class StaticDeviceDAO implements DeviceDAO {
  constructor() {
    this.devices = this.createDevices(100);
  }

  private devices: Device[] = [];

  public async getDeviceCount(): Promise<number> {
    return this.devices.length;
  }

  public async getDevices(searchOptions: SearchOptions): Promise<Device[]> {
    if (searchOptions.batchSize == 0) {
      throw new Error('A batch size greater than zero must be provided.');
    }

    if (searchOptions.offset > this.devices.length) {
      throw new Error('An invalid offset that is greater than the total number of devices was provided.');
    }

    if (searchOptions.sortOptions) {
      if (searchOptions.sortOptions.field === 'operational') {
        this.devices.sort(this.compareBoolean(searchOptions.sortOptions.direction));
      } else {
        this.devices.sort(this.compare(searchOptions.sortOptions.field, searchOptions.sortOptions.direction));
      }
    }

    return this.devices.slice(searchOptions.offset, searchOptions.offset + searchOptions.batchSize);
  }

  // this allows us to sort the static data
  private compare(field: string, direction: SORT_DIRECTION) {
    return function (a, b) {
      if (direction.toString() === 'ASC') {
        return a[field].localeCompare(b[field]);
      } else {
        return b[field].localeCompare(a[field]);
      }
    };
  }

  // this allows us to sort the static data
  private compareBoolean(direction: SORT_DIRECTION) {
    return function (a: Device, b: Device) {
      if (direction.toString() === 'ASC') {
        return a.operational === b.operational ? 0 : a.operational ? -1 : 1;
      } else {
        return b.operational === a.operational ? 0 : b.operational ? -1 : 1;
      }
    };
  }

  private createDevices(count: number): Device[] {
    const devices: Device[] = [];
    let n = 1;
    while (n <= count) {
      const id = `id-${n}`;
      const name = n % 2 == 0 ? `AHU-${n}` : `CDS-${n}`;
      const make: string = `make-${n}`;
      const model: string = n % 3 == 0 ? `AAAA-${n}` : `BBBB-${n}`;
      const site: string = `SG-SIN-MBC${n}`;
      const section: string = `SIN-MBC${n}`;
      const lastPayload: string = new Date(new Date().getTime() - randomInt(1000000000)).toISOString();
      const operational: boolean = n % 3 == 0 ? false : true;
      const tags: string[] = [];
      devices.push({ id, name, make, model, site, section, lastPayload, operational, tags });
      n++;
    }

    return devices;
  }
}
