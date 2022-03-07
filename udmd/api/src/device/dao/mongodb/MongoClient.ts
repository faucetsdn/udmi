import { logger } from '../../../common/logger';
import { MongoClient, Db, MongoClientOptions } from 'mongodb';

// Configuration information required to connect to a mongo database
export interface MongoConfig {
  protocol: string;
  host: string;
  username: string;
  password: string;
  database: string;
}

export async function getMongoDb(
  config: MongoConfig,
  clientOptions: MongoClientOptions = { minPoolSize: 1, maxPoolSize: 10 }
): Promise<Db> {
  // setup connection details
  const credentials = config.username ? `${config.username}:${config.password}@` : '';
  const uri = `${config.protocol}://${credentials}${config.host}`;

  try {
    const client: MongoClient = await MongoClient.connect(uri, clientOptions);
    logger.debug(`Connected to mongo host ${config.host}.`);
    return client.db(config.database);
  } catch (e) {
    logger.error(`Error connecting to mongo host ${config.host} - ${e}`);
    throw e;
  }
}
