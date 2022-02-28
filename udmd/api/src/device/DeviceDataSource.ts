import { GraphQLDataSource } from 'apollo-datasource-graphql/dist/GraphQLDataSource';
import { logger } from '../common/logger';
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
      const id = `id${n}`;
      const name = `name${n}`;
      devices.push({ id, name });
      n++;
    }

    return devices;
  }
}
