import { GraphQLDataSource } from 'apollo-datasource-graphql/dist/GraphQLDataSource';
import { Device, DevicesResponse, SearchOptions } from './model';
import { filterDevices } from './DeviceFilter';

export class DeviceDataSource extends GraphQLDataSource {
  protected context: any;

  private devices: Device[] = [];

  constructor() {
    super();
    this.devices = this.createDevices(100);
  }

  public initialize(config) {
    super.initialize(config);
    this.context = {};
  }

  protected getContext(): any {
    return this.context;
  }

  async getDevices(searchOptions: SearchOptions): Promise<DevicesResponse> {
    const devices: Device[] = filterDevices(this.devices, searchOptions);
    return { devices, totalCount: this.devices.length };
  }

  private createDevices(count: number): Device[] {
    const devices: Device[] = [];
    let n = 1;
    while (n <= count) {
      const id = `id-${n}`;
      const name = `name-${n}`;
      const make: string = `make-${n}`;
      const model: string = `model-${n}`;
      const site: string = `site-${n}`;
      const section: string = `section-${n}`;
      const lastPayload: string = '2022-08-30';
      const operational: boolean = false;
      const tags: string[] = [];
      devices.push({ id, name, make, model, site, section, lastPayload, operational, tags });
      n++;
    }

    return devices;
  }
}
