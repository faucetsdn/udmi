import { GraphQLDataSource } from 'apollo-datasource-graphql/dist/GraphQLDataSource';
import { Device } from './model';

export class DeviceDataSource extends GraphQLDataSource {
  protected context: any;

  constructor() {
    super();
  }

  public initialize(config) {
    super.initialize(config);

    // store context information
    this.context = {};
  }

  protected getContext(): any {
    return this.context;
  }

  async getDevices(): Promise<Device[]> {
    return [
      { id: 'id1', name: 'name1' },
      { id: 'id2', name: 'name2' },
      { id: 'id3', name: 'name3' },
    ];
  }
}
