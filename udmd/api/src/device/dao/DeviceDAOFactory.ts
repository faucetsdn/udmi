import { Configuration } from '../../server/config';
import { DeviceDAO } from './DeviceDAO';
import { getMongoDb } from './mongodb/MongoClient';
import { MongoDeviceDAO } from './mongodb/MongoDeviceDao';
import { StaticDeviceDAO } from './StaticDeviceDAO';
import { Db } from 'mongodb';

export async function getDeviceDAO(config: Configuration): Promise<DeviceDAO> {
  if (config.database === 'MONGO') {
    const db: Db = await getMongoDb({
      protocol: process.env.MONGO_PROTOCOL,
      username: process.env.MONGO_USERNAME,
      password: process.env.MONGO_PASSWORD,
      host: process.env.MONGO_HOST,
      database: process.env.MONGO_DATABASE,
    });
    return new MongoDeviceDAO(db);
  } else {
    return new StaticDeviceDAO();
  }
}
