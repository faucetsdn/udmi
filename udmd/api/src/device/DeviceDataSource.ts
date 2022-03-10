import { GraphQLDataSource } from 'apollo-datasource-graphql/dist/GraphQLDataSource';
import { Device, DevicesResponse, SearchOptions } from './model';
import { DeviceDAO } from './dao/DeviceDAO';

export class DeviceDataSource extends GraphQLDataSource {
  constructor(private deviceDAO: DeviceDAO) {
    super();
  }

  public initialize(config): void {
    super.initialize(config);
  }

  async getDevices(searchOptions: SearchOptions): Promise<DevicesResponse> {
    const totalCount = await this.deviceDAO.getDeviceCount();
    const devices: Device[] = await this.deviceDAO.getDevices(searchOptions);
    const totalFilteredCount: number = await this.deviceDAO.getFilteredDeviceCount(searchOptions);
    return { devices, totalCount, totalFilteredCount };
  }
}
