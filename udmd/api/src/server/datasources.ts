import { DataSources } from 'apollo-server-core/dist/graphqlOptions';
import { DeviceDataSource } from '../device/DeviceDataSource';

export default function dataSources(): () => DataSources<object> {
  return () => ({
    deviceDS: new DeviceDataSource(),
  });
}
