import { Configuration } from '../../server/config';
import { DeviceDAO } from './DeviceDAO';
import { getMongoDb } from './mongodb/MongoClient';
import { MongoDeviceDAO } from './mongodb/MongoDeviceDAO';
import { StaticDeviceDAO } from './static/StaticDeviceDAO';

export async function getDeviceDAO(config: Configuration): Promise<DeviceDAO> {
  if (config.datasource === 'MONGO') {
    return new MongoDeviceDAO(await getMongoDb(config));
  } else {
    return new StaticDeviceDAO();
  }
}
