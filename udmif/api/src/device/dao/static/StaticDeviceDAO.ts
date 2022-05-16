import { randomInt } from 'crypto';
import { v4 as uuid } from 'uuid';
import { fromString } from '../../FilterParser';
import {
  Device,
  DeviceMakesSearchOptions,
  DeviceModelsSearchOptions,
  DeviceNamesSearchOptions,
  Filter,
  Point,
  SearchOptions,
  SectionsSearchOptions,
  SitesSearchOptions,
  SORT_DIRECTION,
} from '../../model';
import { DeviceDAO } from '../DeviceDAO';
import { filterDevices } from './StaticDeviceFilter';

const deviceTemplates = [
  { make: 'Cisco', models: ['Mediator'], name: 'cis' },
  { make: 'BitBox USA', models: ['BitBox'], name: 'bb-usa' },
  { make: 'Automated Logic', models: ['LGR', 'G5CE'], name: 'aut-log' },
  { make: 'Enlightened', models: ['Light Gateway'], name: 'enl' },
  { make: 'Tridium', models: ['JACE 8000'], name: 'tri' },
  { make: 'Delta Controls', models: ['Entelibus Manager 100', 'CopperCube'], name: 'dc' },
  { make: 'Acquisuite', models: ['Obvious AcquiSuite A88 12-1'], name: 'acq' },
  { make: 'Schneider Electric / APC', models: ['PowerLogic ION', 'AP9630', 'AP9631', 'AP9635'], name: 'apc' },
];

const sites = [
  { site: 'site1', sections: ['section-a', 'section-b'] },
  { site: 'site2', sections: ['section-c', 'section-d'] },
  { site: 'site3', sections: ['section-e', 'section-f'] },
];

// this class exists to return static, sorted, and filtered data
export class StaticDeviceDAO implements DeviceDAO {
  constructor() {
    this.devices = this.createDevices(100);
  }

  private devices: Device[] = [];

  public async getDeviceCount(): Promise<number> {
    return this.devices.length;
  }

  public async getPoints(deviceId: string): Promise<Point[]> {
    return this.devices[0].points;
  }

  public async getFilteredDeviceCount(searchOptions: SearchOptions): Promise<number> {
    let filteredDevices = this.devices;
    if (searchOptions.filter) {
      const filters: Filter[] = fromString(searchOptions.filter);
      filteredDevices = filterDevices(filters, filteredDevices);
    }

    return filterDevices.length;
  }

  public async getDevice(id: string): Promise<Device | null> {
    const device = this.devices.find((device) => device.id === id);
    return device ? device : null;
  }

  public async getDevices(searchOptions: SearchOptions): Promise<Device[]> {
    let filteredDevices = this.devices;
    if (searchOptions.filter) {
      const filters: Filter[] = fromString(searchOptions.filter);
      filteredDevices = filterDevices(filters, filteredDevices);
    }

    if (searchOptions.sortOptions) {
      if (searchOptions.sortOptions.field === 'operational') {
        filteredDevices.sort(this.compareBoolean(searchOptions.sortOptions.direction));
      } else {
        filteredDevices.sort(this.compare(searchOptions.sortOptions.field, searchOptions.sortOptions.direction));
      }
    }

    return filteredDevices.slice(searchOptions.offset, searchOptions.offset + searchOptions.batchSize);
  }

  public async getDeviceNames(searchOptions: DeviceNamesSearchOptions): Promise<string[]> {
    return this.getDistinct('name', searchOptions.search, searchOptions.limit);
  }

  public async getDeviceMakes(searchOptions: DeviceMakesSearchOptions): Promise<string[]> {
    return this.getDistinct('make', searchOptions.search, searchOptions.limit);
  }

  public async getDeviceModels(searchOptions: DeviceModelsSearchOptions): Promise<string[]> {
    return this.getDistinct('model', searchOptions.search, searchOptions.limit);
  }

  public async getSites(searchOptions: SitesSearchOptions): Promise<string[]> {
    return this.getDistinct('site', searchOptions.search, searchOptions.limit);
  }

  public async getSections(searchOptions: SectionsSearchOptions): Promise<string[]> {
    return this.getDistinct('section', searchOptions.search, searchOptions.limit);
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
      const deviceTemplate = this.getRandom(deviceTemplates);
      const deviceModel = this.getRandom(deviceTemplate.models);
      const deviceSite = this.getRandom(sites);
      const deviceSection = this.getRandom(deviceSite.sections);

      const id: string = uuid();
      const name: string = `${deviceTemplate.name}-${n}`;
      const make: string = `${deviceTemplate.make}`;
      const model: string = deviceModel;
      const site: string = deviceSite.site;
      const section: string = deviceSection;
      const lastPayload: string = new Date(new Date().getTime() - randomInt(1000000000)).toISOString();
      const operational: boolean = n % 3 == 0 ? false : true;
      const serialNumber: string = `serialNo-${n}`;
      const firmware: string = `v-${n}`;
      const points: Point[] = [];
      devices.push({
        id,
        name,
        make,
        model,
        site,
        section,
        lastPayload,
        operational,
        firmware,
        serialNumber,
        points,
        tags: [],
      });
      n++;
    }

    return devices;
  }

  private getRandom(array: any[]): any {
    return array[Math.floor(Math.random() * array.length)];
  }

  private getDistinct(field: string, search?: string, limit?: number): string[] {
    const results = [...new Set(this.devices.map((item) => item[field]))];

    if (search) {
      return results.filter((item) => item.toLowerCase().includes(search.toLowerCase())).slice(0, limit);
    } else {
      return results.slice(0, limit);
    }
  }
}
