import { logger } from '../../../common/logger';
import { MongoClient, Db, MongoClientOptions } from 'mongodb';
import { Configuration } from '../../../server/config';

export async function getMongoDb(systemConfiguration: Configuration): Promise<Db> {
  // get the mongo specific configuration from the system config
  const config: MongoConfig = getMongoConfig(systemConfiguration);

  // setup connection details
  const credentials = config.username ? `${config.username}:${config.password}@` : '';
  const uri = `${config.protocol}://${credentials}${config.host}`;

  try {
    const client: MongoClient = await MongoClient.connect(uri, getMongoClientOptions(systemConfiguration));
    logger.debug(`Connected to mongo host ${config.host}.`);
    return client.db(config.database);
  } catch (e) {
    logger.error(`Error connecting to mongo host ${config.host} - ${e}`);
    throw e;
  }
}

// Configuration information required to connect to a mongo database
interface MongoConfig {
  protocol: string;
  host: string;
  username: string;
  password: string;
  database: string;
}

function getMongoConfig(config: Configuration): MongoConfig {
  return {
    protocol: config.mongoProtocol,
    username: config.mongoUsername,
    password: config.mongoPassword,
    host: config.mongoHost,
    database: config.mongoDatabase,
  };
}

function getMongoClientOptions(config: Configuration): MongoClientOptions {
  return { minPoolSize: 1, maxPoolSize: 10 };
}
