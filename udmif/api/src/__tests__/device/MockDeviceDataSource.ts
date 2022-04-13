import { GraphQLDataSource } from 'apollo-datasource-graphql/dist/GraphQLDataSource';
import { Device, DevicesResponse, Point, SearchOptions } from '../../device/model';
import { createDevices } from './data';

export default class MockDeviceDataSource extends GraphQLDataSource<object> {
  constructor() {
    super();
  }

  public initialize(config) {
    super.initialize(config);
  }

  async getDevices(searchOptions: SearchOptions): Promise<DevicesResponse> {
    const devices: Device[] = createDevices(30);
    return { devices, totalCount: 30, totalFilteredCount: 10 };
  }

  async getDevice(id: string): Promise<Device> {
    return createDevices(1)[0];
  }

  async getPoints(deviceId: string): Promise<Point[]> {
    return createDevices(1)[0].points;
  }
}
