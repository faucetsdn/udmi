import { GraphQLDataSource } from 'apollo-datasource-graphql/dist/GraphQLDataSource';
import { Device, DevicesResponse, SearchOptions } from '../../device/model';
import { createDevices } from './data';

export default class MockDeviceDataSource extends GraphQLDataSource<object> {
  constructor() {
    super();
  }

  protected context: any;

  public initialize(config) {
    super.initialize(config);
    this.context = {};
  }

  protected getContext(): any {
    return this.context;
  }

  async getDevices(searchOptions: SearchOptions): Promise<DevicesResponse> {
    const devices: Device[] = createDevices(30);
    return { devices, totalCount: 30 };
  }
}
