import { GraphQLDataSource } from 'apollo-datasource-graphql/dist/GraphQLDataSource';
import { sum } from 'lodash';
import { Device, DevicesResponse, Point } from '../../device/model';
import { createDevices } from './data';

export default class MockDeviceDataSource extends GraphQLDataSource<object> {
  constructor() {
    super();
  }

  public initialize(config) {
    super.initialize(config);
  }

  async getDevices(): Promise<DevicesResponse> {
    const devices: Device[] = createDevices(30);
    return { devices, totalCount: 30, totalFilteredCount: 10 };
  }

  async getDevice(): Promise<Device> {
    return createDevices(1)[0];
  }

  async getPoints(): Promise<Point[]> {
    return createDevices(1)[0].points;
  }

  async getDeviceNames(): Promise<string[]> {
    return createDevices(10)
      .map((d) => d.name)
      .sort();
  }

  async getDeviceMakes(): Promise<string[]> {
    return createDevices(10)
      .map((d) => d.make)
      .sort();
  }

  async getDeviceModels(): Promise<string[]> {
    return createDevices(10)
      .map((d) => d.model)
      .sort();
  }

  async getSections(): Promise<string[]> {
    return createDevices(10)
      .map((d) => d.section)
      .sort();
  }

  async getDevicesBySite(siteName: string): Promise<DevicesResponse> {
    const response = await this.getDevices();

    return {
      ...response,
      devices: response.devices.filter((d) => d.site === siteName),
    };
  }

  async getDeviceErrorsCountBySite(siteName: string): Promise<number> {
    const { devices } = await this.getDevicesBySite(siteName);

    return devices.reduce((n: number, device: Device) => {
      return sum([n, device.validation?.errors.length]);
    }, 0);
  }
}
