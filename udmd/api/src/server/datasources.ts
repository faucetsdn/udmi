import { DataSources } from 'apollo-server-core/dist/graphqlOptions';
import { DeviceDAO } from '../device/dao/DeviceDAO';
import { DeviceDataSource } from '../device/DeviceDataSource';

export default function dataSources(deviceDAO: DeviceDAO): () => DataSources<object> {
  return () => ({
    deviceDS: new DeviceDataSource(deviceDAO),
  });
}
