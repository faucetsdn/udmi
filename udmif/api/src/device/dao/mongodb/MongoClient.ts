import { logger } from '../../../common/logger';
import { MongoClient, Db, MongoClientOptions } from 'mongodb';
import { Configuration } from '../../../server/config';

export async function getMongoDb(systemConfiguration: Configuration): Promise<Db> {
  const uri = getUri(systemConfiguration);
  const host = systemConfiguration.mongoHost;

  try {
    const client: MongoClient = await MongoClient.connect(uri, getClientOptions());
    logger.debug(`Connected to mongo host ${host}.`);
    return client.db(systemConfiguration.mongoDatabase);
  } catch (e) {
    logger.error(`Error connecting to mongo host ${host} - ${e}`);
    throw e;
  }
}

function getUri(systemConfiguration: Configuration): string {
  // extracting for readability
  const userName = systemConfiguration.mongoUsername;
  const password = systemConfiguration.mongoPassword;
  const host = systemConfiguration.mongoHost;
  const protocol = systemConfiguration.mongoProtocol;

  logger.debug(`Attempting to connect to mongo as user: '${userName}'`);

  // set up the uri
  const credentials = userName ? `${userName}:${password}@` : '';
  return `${protocol}://${credentials}${host}`;
}

/**
 * Get the options for the Mongo Client. Connections are managed by specifying the min and max number of connections in the connection pool.
 * If we need more flexibility in controlling the client, we can add configuration values to the environment and set them here.
 * @returns the Mongo Client Options
 */
function getClientOptions(): MongoClientOptions {
  return { minPoolSize: 1, maxPoolSize: 10 };
}
