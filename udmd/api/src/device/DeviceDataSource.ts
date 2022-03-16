import { GraphQLDataSource } from 'apollo-datasource-graphql/dist/GraphQLDataSource';
import { Device, DevicesResponse, Point, SearchOptions } from './model';
import { DeviceDAO } from './dao/DeviceDAO';
import { validate } from './SearchOptionsValidator';

export class DeviceDataSource extends GraphQLDataSource {
  constructor(private deviceDAO: DeviceDAO) {
    super();
  }

  public initialize(config): void {
    super.initialize(config);
  }

  async getDevices(searchOptions: SearchOptions): Promise<DevicesResponse> {
    const validatedSearchOptions: SearchOptions = validate(searchOptions);

    const devices: Device[] = await this.deviceDAO.getDevices(validatedSearchOptions);
    const totalCount = await this.deviceDAO.getDeviceCount();
    const totalFilteredCount: number = await this.deviceDAO.getFilteredDeviceCount(validatedSearchOptions);

    return { devices, totalCount, totalFilteredCount };
  }

  async getDevice(id: string): Promise<Device> {
    return this.deviceDAO.getDevice(id);
  }

  async getPoints(deviceId: string): Promise<Point[]> {
    return this.deviceDAO.getPoints(deviceId);
  }
}
