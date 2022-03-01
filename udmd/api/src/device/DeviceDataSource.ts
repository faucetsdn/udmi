import { GraphQLDataSource } from 'apollo-datasource-graphql/dist/GraphQLDataSource';
import { Device, DevicesResponse, SearchOptions } from './model';
import { batchDevices } from './DeviceFilter';

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
    const devices: Device[] = batchDevices(this.devices, searchOptions);
    return { devices, totalCount: this.devices.length };
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
      const lastPayload: string = '2022-08-30';
      const operational: boolean = n % 3 == 0 ? false : true;
      const tags: string[] = [];
      devices.push({ id, name, make, model, site, section, lastPayload, operational, tags });
      n++;
    }

    return devices;
  }
}
